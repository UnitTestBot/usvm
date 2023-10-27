package org.usvm.api.crash

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInst
import org.usvm.SolverType
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.UPriorityCollection
import org.usvm.api.targets.JcTarget
import org.usvm.constraints.UPathConstraints
import org.usvm.logger
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcComponents
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcTypeSystem
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.WeightedPathSelector
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.stopstrategies.GroupedStopStrategy
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.TimeoutStopStrategy
import org.usvm.targets.UProofObligation
import org.usvm.util.log2
import org.usvm.util.originalInst
import kotlin.time.Duration

class JcCrashReproduction(val cp: JcClasspath, private val timeout: Duration) : UMachine<JcState>() {
    data class CrashStackTraceFrame(
        val method: JcMethod,
        val inst: JcInst
    )

    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem, solverType = SolverType.YICES, useSolverForForks = true)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph, observer = null)

    private val cfgStatistics = transparentCfgStatistics()

    private lateinit var resultState: JcState
    private var crashReproductionComplete: Boolean = false

    private val pobManager = StatePobManager()

    fun reproduceCrash(
        crashException: JcClassOrInterface,
        crashStackTrace: List<CrashStackTraceFrame>
    ): Boolean {
        crashException.let { } // todo: check exception type
        pobManager.addPob(level = 0, pob = UPathConstraints(ctx))

        val initialStates = mkInitialStates(crashStackTrace)

        val pathSelector = PobPathSelector(crashStackTrace.size) { closestTargetPs() }
        pathSelector.add(initialStates)

        run(
            interpreter,
            pathSelector,
            observer = PobPropagator(),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = GroupedStopStrategy(
                TimeoutStopStrategy(timeout.inWholeMilliseconds, System::currentTimeMillis),
                StopStrategy { crashReproductionComplete }
            ),
        )

        return crashReproductionComplete
    }

    private fun mkInitialStates(
        crashStackTrace: List<CrashStackTraceFrame>
    ): List<JcState> {
        val states = mutableListOf<JcState>()
        var prevTarget: JcLevelTarget? = null
        for ((level, entry) in crashStackTrace.asReversed().withIndex()) {
            val target = JcLevelTarget(entry.inst, level)
            prevTarget?.let { target.addChild(it) }
            states += interpreter.getInitialState(entry.method, listOf(target))
            prevTarget = target
        }
        return states
    }

    private fun transparentCfgStatistics() = object : CfgStatistics<JcMethod, JcInst> {
        private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

        override fun getShortestDistance(method: JcMethod, stmtFrom: JcInst, stmtTo: JcInst): UInt {
            return cfgStatistics.getShortestDistance(method, stmtFrom.originalInst(), stmtTo.originalInst())
        }

        override fun getShortestDistanceToExit(method: JcMethod, stmtFrom: JcInst): UInt {
            return cfgStatistics.getShortestDistanceToExit(method, stmtFrom.originalInst())
        }
    }

    private fun isStateTerminated(state: JcState): Boolean =
        state.callStack.isEmpty()

    override fun close() {
        components.close()
    }

    private fun JcState.levelTarget(): JcLevelTarget = targets
        .filterIsInstance<JcLevelTarget>()
        .singleOrNull()
        ?: error("Wrong level target")

    private data class JcLevelTarget(
        override val location: JcInst,
        val level: Int
    ) : JcTarget(location)

    private inner class StatePobManager {
        private val levelPobs = hashMapOf<Int, MutableSet<UPathConstraints<JcType>>>()
        private val levelStates = hashMapOf<Int, MutableSet<JcState>>()

        private fun levelPobs(level: Int): MutableSet<UPathConstraints<JcType>> =
            levelPobs.getOrPut(level) { hashSetOf() }

        private fun levelStates(level: Int): MutableSet<JcState> =
            levelStates.getOrPut(level) { hashSetOf() }

        fun currentLevel() = levelPobs.keys.max()

        fun addPob(level: Int, pob: UPathConstraints<JcType>) {
            logger.info { "Found POB at level: $level | ${levelPobs(level).size}" }

            levelPobs(level).add(pob)

            for (state in levelStates(level)) {
                val target = state.levelTarget()
                check(target.level == level) { "Unexpected state" }
                propagateBackward(target, pob, state)
            }
        }

        fun onLevelTargetReach(state: JcState) {
            if (state.models.isEmpty()) {
                return // unsat state
            }

            val target = state.levelTarget()

            // backward
            for (pob in levelPobs(target.level)) {
                propagateBackward(target, pob, state)
            }

            levelStates(target.level).add(state.clone())

            // todo: forward (propagate target)
        }

        private fun propagateBackward(
            level: JcLevelTarget,
            pob: UPathConstraints<JcType>,
            state: JcState
        ) {
            if (crashReproductionComplete) return

            val wp = UProofObligation.weakestPrecondition(pob, state) ?: return

            val prevLevel = level.parent as? JcLevelTarget
            if (prevLevel == null) {
                resultState = state.clone()
                crashReproductionComplete = true
                return
            }

            addPob(prevLevel.level, wp)
        }
    }

    private inner class PobPathSelector(
        numLevels: Int,
        private val basePsFactory: () -> UPathSelector<JcState>
    ) : UPathSelector<JcState> {
        private val levelStats = MutableList(numLevels) { 0 }
        private val levelPs = List(numLevels) { basePsFactory() }

        override fun isEmpty(): Boolean =
            levelPs.all { it.isEmpty() }

        private fun shouldConsiderPreviousLevel(level: Int): Boolean {
            if (level == 0) return false
            val curLevel = levelStats[level]
            val prevLevel = levelStats[level - 1]

            return curLevel > 2 * prevLevel
        }

        override fun peek(): JcState {
            var level = pobManager.currentLevel()
            while (level >= 0) {
                if (shouldConsiderPreviousLevel(level)) {
                    level--
                    continue
                }

                val ps = levelPs[level]
                if (ps.isEmpty()) {
                    level--
                    continue
                }

                levelStats[level]++
                return ps.peek()
            }

            return levelPs.first { !it.isEmpty() }.peek()
        }

        override fun update(state: JcState) {
            levelPs[state.level()].update(state)
        }

        override fun add(states: Collection<JcState>) {
            states.forEach { addStateToLevel(it) }
        }

        override fun remove(state: JcState) {
            levelPs[state.level()].remove(state)
        }

        private fun addStateToLevel(state: JcState) {
            levelPs[state.level()].add(listOf(state))
        }

        private fun JcState.level(): Int = levelTarget().level
    }

    private inner class PobPropagator : UMachineObserver<JcState> {
        override fun onState(parent: JcState, forks: Sequence<JcState>) {
            checkLevelTarget(parent)
            forks.forEach { checkLevelTarget(it) }
        }

        private fun checkLevelTarget(state: JcState) {
            val target = state.levelTarget()
            if (target.isTerminal) {
                if (target.location == state.currentStatement) {
                    pobManager.onLevelTargetReach(state)
                }
            } else {
                val prevStatement = state.pathLocation.parent?.statement
                if (prevStatement is JcConcreteMethodCallInst && target.location == prevStatement.originalInst()) {
                    pobManager.onLevelTargetReach(state)
                }
            }
        }
    }

    private fun closestTargetPs(): UPathSelector<JcState> {
        val distanceCalculator = LevelTargetDistanceCalculator()
        return DistancePathSelector(weighter = distanceCalculator::calculateDistance)
    }

    private fun targetFrameDistance(statement: JcInst, target: JcInst): UInt {
        val method = statement.location.method
        check(method == target.location.method) { "Non local distance" }
        val distance = cfgStatistics.getShortestDistance(method, statement, target)
        if (distance == UInt.MAX_VALUE) return distance
        return distance * 64u
    }

    private fun targetFrameDistanceAfterReturn(statement: JcInst, target: JcInst): UInt =
        applicationGraph.successors(statement)
            .map { targetFrameDistance(it, target) }
            .minOrNull()
            ?: UInt.MAX_VALUE

    // todo: check call stacks (avoid recursion)
    private inner class LevelTargetDistanceCalculator {
        fun calculateDistance(state: JcState): UInt {
            val callStack = state.callStack
            if (callStack.isEmpty()) return 1024u

            val targetLocation = state.levelTarget().location
            val currentStatement = state.currentStatement.originalInst()

            // todo: target is always on the first frame
            if (callStack.size == 1) {
                return if (state.methodResult !is JcMethodResult.Success) {
                    targetFrameDistance(currentStatement, targetLocation)
                } else {
                    targetFrameDistanceAfterReturn(currentStatement, targetLocation)
                }
            }


            val statementMethod = applicationGraph.methodOf(currentStatement)
            check(statementMethod == callStack.last().method) {
                "Statement method not in stack"
            }

            var callStackDistance = 0u
            var current = currentStatement
            for ((method, returnStatement) in callStack.asReversed().dropLast(1)) {
                val distanceToExit = cfgStatistics.getShortestDistanceToExit(method, current)
                if (distanceToExit == UInt.MAX_VALUE) return UInt.MAX_VALUE

                callStackDistance = callStackDistance * 2u + distanceToExit
                current = returnStatement ?: error("No return statement")
            }

            val distanceToTarget = targetFrameDistanceAfterReturn(current, targetLocation)
            if (distanceToTarget == UInt.MAX_VALUE) return UInt.MAX_VALUE

            return distanceToTarget + log2(callStackDistance)
        }
    }

    class DistancePathSelector(
        private val weighter: StateWeighter<JcState, UInt>
    ) : UPathSelector<JcState> {
        private val priorityCollection = DeterministicPriorityCollection<JcState, UInt>(Comparator.naturalOrder())

        override fun isEmpty(): Boolean = priorityCollection.count == 0

        override fun peek(): JcState = priorityCollection.peek()

        override fun update(state: JcState) {
            val weight = weighter.weight(state)
            if (weight == UInt.MAX_VALUE) {
                priorityCollection.remove(state)
                return
            }
            priorityCollection.update(state, weight)
        }

        override fun add(states: Collection<JcState>) {
            for (state in states) {
                val weight = weighter.weight(state)
                if (weight == UInt.MAX_VALUE) continue
                priorityCollection.add(state, weight)
            }
        }

        override fun remove(state: JcState) = priorityCollection.remove(state)
    }
}
