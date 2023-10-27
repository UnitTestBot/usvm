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
import org.usvm.api.targets.JcTarget
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcComponents
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcTypeSystem
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.ps.WeightedPathSelector
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.stopstrategies.GroupedStopStrategy
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.TimeoutStopStrategy
import org.usvm.targets.UProofObligation
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
                resultState = state
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

        override fun peek(): JcState {
            var level = pobManager.currentLevel()
            while (level >= 0) {
                val ps = levelPs[level]
                if (!ps.isEmpty()) {
                    levelStats[level]++
                    return ps.peek()
                }
                level--
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
        // todo: check call stacks (avoid recursion)
        val distanceCalculator = MultiTargetDistanceCalculator<JcMethod, JcInst, _> { loc ->
            CallStackDistanceCalculator(
                targets = listOf(loc),
                cfgStatistics = cfgStatistics,
                applicationGraph = applicationGraph
            )
        }

        fun calculateDistanceToTargets(state: JcState) =
            state.targets.minOfOrNull { target ->
                val location = target.location
                if (location == null) {
                    0u
                } else {
                    distanceCalculator.calculateDistance(
                        state.currentStatement,
                        state.callStack,
                        location
                    )
                }
            } ?: UInt.MAX_VALUE

        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = ::calculateDistanceToTargets
        )
    }
}
