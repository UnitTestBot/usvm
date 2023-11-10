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
import org.usvm.algorithms.RandomizedPriorityCollection
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
import org.usvm.merging.MutableMergeGuard
import org.usvm.ps.StateWeighter
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.stopstrategies.GroupedStopStrategy
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.TimeoutStopStrategy
import org.usvm.targets.UProofObligation
import org.usvm.util.log2
import org.usvm.util.originalInst
import java.util.IdentityHashMap
import kotlin.random.Random
import kotlin.time.Duration

class JcCrashReproduction(val cp: JcClasspath, private val timeout: Duration) : UMachine<JcState>() {
    data class CrashStackTraceFrame(
        val method: JcMethod,
        val inst: JcInst
    )

    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(
        typeSystem, solverType = SolverType.YICES,
        useSolverForForks = true, runSolverInAnotherProcess = false
    )
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
        pobManager.addPob(level = 0, newPob = UPathConstraints(ctx))

        val initialStates = mkInitialStates(crashStackTrace)

        val pathSelector = PobPathSelector(crashStackTrace.size) { level -> closestTargetPs(level) }
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

        fun addPob(level: Int, newPob: UPathConstraints<JcType>) {
            logger.info { "Found POB at level: $level | ${levelPobs(level).size}" }

            val pob = mergePobs(newPob, levelPobs(level))
            levelPobs(level).add(pob)

            for (state in levelStates(level)) {
                val target = state.levelTarget()
                check(target.level == level) { "Unexpected state" }
                propagateBackward(target, pob, state)
            }
        }

        fun mergePobs(
            newPob: UPathConstraints<JcType>,
            levelPobs: MutableSet<UPathConstraints<JcType>>
        ): UPathConstraints<JcType> {
            var result = newPob
            val removedPobs = mutableListOf<UPathConstraints<JcType>>()
            for (levelPob in levelPobs) {
                val guard = MutableMergeGuard(ctx)
                val merged = levelPob.clone().mergeWith(result, guard) ?: continue
                merged += ctx.mkOr(guard.thisConstraint, guard.otherConstraint)

                logger.info { "Merged POB" }
                result = merged
                removedPobs += levelPob
            }

            levelPobs.removeAll(removedPobs)
            return result
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
        private val basePsFactory: (Int) -> UPathSelector<JcState>
    ) : UPathSelector<JcState> {
        private val levelStats = MutableList(numLevels) { 0 }
        private val levelPs = List(numLevels) { level -> basePsFactory(level) }

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
                val prevStatement = state.pathNode.parent?.statement
                if (prevStatement is JcConcreteMethodCallInst && target.location == prevStatement.originalInst()) {
                    pobManager.onLevelTargetReach(state)
                }
            }
        }
    }

    private fun closestTargetPs(level: Int): UPathSelector<JcState> {
        val distanceCalculator = LevelTargetDistanceCalculator()
//        return DistancePathSelector(weighter = distanceCalculator::calculateDistance)
        return DistancePathSelectorWithLocalBuckets(level, weighter = distanceCalculator::calculateDistance)
    }

    private fun targetFrameDistance(statement: JcInst, target: JcInst): UInt {
        val method = statement.location.method
        check(method == target.location.method) { "Non local distance" }
        val distance = cfgStatistics.getShortestDistance(method, statement, target)
        if (distance == UInt.MAX_VALUE) return distance
        return distance * LOCAL_DISTANCE_SHIFT
    }

    private fun targetFrameDistanceAfterReturn(statement: JcInst, target: JcInst): UInt =
        applicationGraph.successors(statement)
            .map { targetFrameDistance(it, target) }
            .minOrNull()
            ?: UInt.MAX_VALUE

    // todo: check call stacks (avoid recursion)
    private inner class LevelTargetDistanceCalculator {
        fun calculateDistance(state: JcState): StateDistance {
            if (state.callStack.isEmpty()) return StateDistance(1024u)
            return calculateDistanceToLevelTarget(state)
        }

        private fun calculateDistanceToLevelTarget(state: JcState): StateDistance {
            val callStack = state.callStack
            val targetLocation = state.levelTarget().location
            val currentStatement = state.currentStatement.originalInst()

            // todo: target is always on the first frame
            if (callStack.size == 1) {
                val distance = if (state.methodResult !is JcMethodResult.Success) {
                    targetFrameDistance(currentStatement, targetLocation)
                } else {
                    targetFrameDistanceAfterReturn(currentStatement, targetLocation)
                }
                return StateDistance(distance, distance / LOCAL_DISTANCE_SHIFT, currentStatement)
            }


            val statementMethod = applicationGraph.methodOf(currentStatement)
            check(statementMethod == callStack.last().method) {
                "Statement method not in stack"
            }

            var callStackDistance = 0u
            var current = currentStatement
            for ((method, returnStatement) in callStack.asReversed().dropLast(1)) {
                val distanceToExit = cfgStatistics.getShortestDistanceToExit(method, current)
                if (distanceToExit == UInt.MAX_VALUE) return StateDistance(UInt.MAX_VALUE)

                callStackDistance = callStackDistance * 2u + distanceToExit
                current = returnStatement ?: error("No return statement")
            }

            val distanceToTarget = targetFrameDistanceAfterReturn(current, targetLocation)
            if (distanceToTarget == UInt.MAX_VALUE) return StateDistance(UInt.MAX_VALUE)

            val distance = distanceToTarget + log2(callStackDistance)
            return StateDistance(distance, distanceToTarget / LOCAL_DISTANCE_SHIFT, current)
        }
    }

    data class StateDistance(
        val distanceToTarget: UInt,
        val localDistanceToTarget: UInt? = null,
        val localInst: JcInst? = null
    ) {
        val isInfinite: Boolean
            get() = distanceToTarget == UInt.MAX_VALUE
    }

    class DistancePathSelector(
        private val weighter: StateWeighter<JcState, StateDistance>
    ) : UPathSelector<JcState> {
        private val priorityCollection = DeterministicPriorityCollection<JcState, StatePriority>(
            compareBy<StatePriority> { it.distance }.thenBy { it.stateId }
        )

        override fun isEmpty(): Boolean = priorityCollection.count == 0

        override fun peek(): JcState = priorityCollection.peek()

        override fun update(state: JcState) {
            val weight = weighter.weight(state)
            if (weight.isInfinite) {
                remove(state)
                return
            }

            priorityCollection.update(state, StatePriority(weight.distanceToTarget, state.id))
        }

        override fun add(states: Collection<JcState>) {
            for (state in states) {
                val weight = weighter.weight(state)
                if (weight.isInfinite) continue

                priorityCollection.add(state, StatePriority(weight.distanceToTarget, state.id))
            }
        }

        override fun remove(state: JcState) = priorityCollection.remove(state)

        private data class StatePriority(
            val distance: UInt,
            val stateId: UInt
        )
    }

    class DistancePathSelectorWithLocalBuckets(
        private val level: Int,
        private val weighter: StateWeighter<JcState, StateDistance>
    ) : UPathSelector<JcState> {
        private val random = Random(17)
        private val stateBucketIds = IdentityHashMap<JcState, BucketId>()
        private val bucketSelector = RandomizedPriorityCollection<BucketId>(
            compareBy { it.hashCode() }
        ) { random.nextDouble() }

        private val stateDistances = IdentityHashMap<JcState, StateDistance>()
        private val stateBuckets = hashMapOf<UInt, UPriorityCollection<JcState, JcState>>()
        private fun getStateBucket(id: BucketId): UPriorityCollection<JcState, JcState> =
            stateBuckets.getOrPut(id.id) {
                DeterministicPriorityCollection(
                    compareBy<JcState> { state ->
                        val distance = stateDistances[state] ?: error("No distance")
                        distance.distanceToTarget
                    }.thenBy { state -> state.id }
                )
            }

        private fun removeBucketIfEmpty(id: BucketId) {
            val bucket = stateBuckets[id.id]
            if (bucket?.count == 0) {
                stateBuckets.remove(id.id)
            }
        }

        override fun isEmpty(): Boolean = stateBuckets.isEmpty()

        private var currentBucket: UPriorityCollection<JcState, *>? = null
        private var bucketLimit = 0

        override fun peek(): JcState {
            check(bucketSelector.count == stateBuckets.values.sumOf { it.count }) { "States count mismatch" }

            if (bucketLimit == 0 || currentBucket?.count == 0) {
                bucketLimit = BUCKET_STEP_LIMIT
                currentBucket = null
            }

            bucketLimit--

            if (currentBucket == null) {
                val bucketId = bucketSelector.peek()
                currentBucket = getStateBucket(bucketId)

                logger.debug { "Level $level select bucket: ${bucketId.id}" }
            }

            return currentBucket!!.peek()
        }

        override fun add(states: Collection<JcState>) {
            for (state in states) {
                val weight = weighter.weight(state)
                if (weight.isInfinite) continue

                val localDistance = weight.localDistanceToTarget ?: UInt.MAX_VALUE
                val bucketId = BucketId(localDistance)
                stateBucketIds[state] = bucketId
                bucketSelector.add(bucketId, 1 / localDistance.coerceAtLeast(1u).toDouble())

                stateDistances[state] = weight
                val bucket = getStateBucket(bucketId)
                bucket.add(state, state)
            }
        }

        override fun remove(state: JcState) {
            val bucketId = stateBucketIds.remove(state) ?: error("No bucket id")
            bucketSelector.remove(bucketId)

            val bucket = getStateBucket(bucketId)
            bucket.remove(state)
            stateDistances.remove(state)

            removeBucketIfEmpty(bucketId)
        }

        private class BucketId(val id: UInt)

        override fun update(state: JcState) {
            remove(state)
            add(listOf(state))
        }
    }

    companion object {
        private const val LOCAL_DISTANCE_SHIFT = 64u
        private const val BUCKET_STEP_LIMIT = 42
    }
}
