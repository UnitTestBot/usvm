package org.usvm.machine.targets

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.callExpr
import org.usvm.UCallStack
import org.usvm.api.targets.TsTarget
import org.usvm.forkblacklists.TargetsReachableForkBlackList
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TsGraph
import org.usvm.machine.state.TsState
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.DistanceCalculator
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import java.util.Collections
import java.util.IdentityHashMap

const val TS_TARGET_UNREACHABLE_PRUNED_REASON: String = "unreachable_pruned"

/**
 * A snapshot of the target-pruning work performed by one [TsMachine][org.usvm.machine.TsMachine].
 *
 * [activeTargetPairs] is deliberately reported independently from [unreachablePruned]: it exposes the
 * `fork candidates x active targets` component even when pruning makes the explored state count smaller.
 */
data class TsTargetReachabilityPruningStatistics(
    val forkCandidates: Long = 0,
    val activeTargetPairs: Long = 0,
    val effectiveTargetPairs: Long = 0,
    val maxActiveTargets: Int = 0,
    val distanceEvaluations: Long = 0,
    val distanceEvaluationNanos: Long = 0,
    val distanceCalculatorsCreated: Long = 0,
    val cacheInvalidations: Long = 0,
    val missingLocationTargets: Long = 0,
    val indeterminateDistances: Long = 0,
    val provenUnreachableDistances: Long = 0,
    val unreachablePruned: Long = 0,
) {
    /** Stable telemetry bridge without a dependency on a report implementation. */
    fun rejectionCount(reasonCode: String): Long =
        if (reasonCode == TS_TARGET_UNREACHABLE_PRUNED_REASON) unreachablePruned else 0
}

/**
 * TS adapter for the JVM [MultiTargetDistanceCalculator] + [TargetsReachableForkBlackList] pattern.
 *
 * The adapter always obtains targets from [TsState.targets]. A target at the current statement is projected to
 * its children for the decision being made: [ReachabilityObserver][org.usvm.api.targets.ReachabilityObserver]
 * performs the corresponding real propagation immediately after the step. Missing/foreign locations, an
 * inconsistent call stack, distance failures, and an infinite distance in the presence of a possible call are
 * all treated as indeterminate and therefore never prune a fork.
 */
class TsTargetReachabilityPruner(
    private val applicationGraph: TsGraph,
    private val cfgStatistics: CfgStatistics<EtsMethod, EtsStmt>,
    private val callGraphStatistics: CallGraphStatistics<EtsMethod>,
    private val nanoTime: () -> Long = { System.nanoTime() },
) : UForkBlackList<TsState, EtsStmt> {
    private enum class ReachabilityEvidence {
        REACHABLE,
        PROVEN_UNREACHABLE,
        INDETERMINATE,
    }

    private data class ReachableFrame(
        val statements: Set<EtsStmt>,
        val reachesExit: Boolean,
    )

    private val knownStatementsByMethod = hashMapOf<EtsMethod, List<EtsStmt>>()
    private val cachedTargetLocations: MutableSet<EtsStmt> = identitySet()
    private val observedTargets: MutableSet<TsTarget> = identitySet()

    private var forkCandidates = 0L
    private var activeTargetPairs = 0L
    private var effectiveTargetPairs = 0L
    private var maxActiveTargets = 0
    private var distanceEvaluations = 0L
    private var distanceEvaluationNanos = 0L
    private var distanceCalculatorsCreated = 0L
    private var cacheInvalidations = 0L
    private var missingLocationTargets = 0L
    private var indeterminateDistances = 0L
    private var provenUnreachableDistances = 0L
    private var unreachablePruned = 0L

    private val distanceCalculator = MultiTargetDistanceCalculator<EtsMethod, EtsStmt, ReachabilityEvidence> { target ->
        cachedTargetLocations.add(target)
        distanceCalculatorsCreated++

        val delegate = InterprocDistanceCalculator(
            targetLocation = target,
            applicationGraph = applicationGraph,
            cfgStatistics = cfgStatistics,
            callGraphStatistics = callGraphStatistics,
        )

        DistanceCalculator { currentStatement, callStack ->
            calculateEvidence(delegate, target, currentStatement, callStack)
        }
    }

    private val reachableTargets = TargetsReachableForkBlackList<
        TsState,
        TsTarget,
        EtsMethod,
        EtsStmt,
        ReachabilityEvidence
        >(
        distanceCalculator = distanceCalculator,
        shouldBlackList = { this == ReachabilityEvidence.PROVEN_UNREACHABLE },
    )

    val statistics: TsTargetReachabilityPruningStatistics
        get() = TsTargetReachabilityPruningStatistics(
            forkCandidates = forkCandidates,
            activeTargetPairs = activeTargetPairs,
            effectiveTargetPairs = effectiveTargetPairs,
            maxActiveTargets = maxActiveTargets,
            distanceEvaluations = distanceEvaluations,
            distanceEvaluationNanos = distanceEvaluationNanos,
            distanceCalculatorsCreated = distanceCalculatorsCreated,
            cacheInvalidations = cacheInvalidations,
            missingLocationTargets = missingLocationTargets,
            indeterminateDistances = indeterminateDistances,
            provenUnreachableDistances = provenUnreachableDistances,
            unreachablePruned = unreachablePruned,
        )

    /** Clear per-analysis caches and counters when a machine instance is reused. */
    fun reset() {
        cachedTargetLocations.toList().forEach(distanceCalculator::removeTargetFromCache)
        cachedTargetLocations.clear()
        observedTargets.clear()

        forkCandidates = 0
        activeTargetPairs = 0
        effectiveTargetPairs = 0
        maxActiveTargets = 0
        distanceEvaluations = 0
        distanceEvaluationNanos = 0
        distanceCalculatorsCreated = 0
        cacheInvalidations = 0
        missingLocationTargets = 0
        indeterminateDistances = 0
        provenUnreachableDistances = 0
        unreachablePruned = 0
    }

    override fun shouldForkTo(state: TsState, stmt: EtsStmt): Boolean {
        val activeTargets = state.targets.toList()
        val currentStatement = runCatching { state.currentStatement }.getOrNull()
        val projectsCurrentTarget = currentStatement != null && activeTargets.any { it.location == currentStatement }
        val effectiveTargets =
            if (projectsCurrentTarget) {
                activeTargets.flatMap { projectTargetAtCurrentStatement(it, currentStatement!!) }
            } else {
                activeTargets
            }

        observedTargets.addAll(activeTargets)
        observedTargets.addAll(effectiveTargets)
        invalidateRemovedTargets()

        forkCandidates++
        activeTargetPairs += activeTargets.size
        effectiveTargetPairs += effectiveTargets.size
        maxActiveTargets = maxOf(maxActiveTargets, activeTargets.size)
        missingLocationTargets += effectiveTargets.count { it.location == null }

        // Once this state has no pending target, target pruning must not become a general exploration stop.
        if (effectiveTargets.isEmpty()) {
            return true
        }

        val shouldFork =
            if (projectsCurrentTarget) {
                effectiveTargets.any { target ->
                    val targetLocation = target.location ?: return@any true
                    distanceCalculator.calculateDistance(stmt, state.callStack, targetLocation) !=
                        ReachabilityEvidence.PROVEN_UNREACHABLE
                }
            } else {
                reachableTargets.shouldForkTo(state, stmt)
            }

        if (!shouldFork) {
            unreachablePruned++
        }
        return shouldFork
    }

    private fun projectTargetAtCurrentStatement(target: TsTarget, currentStatement: EtsStmt): List<TsTarget> {
        if (target.location != currentStatement) {
            return listOf(target)
        }
        if (target.isTerminal) {
            return emptyList()
        }
        return target.children.filterNot { it.isRemoved }
    }

    private fun invalidateRemovedTargets() {
        val removedTargets = observedTargets.filter { it.isRemoved }
        if (removedTargets.isEmpty()) {
            return
        }

        val liveLocations = observedTargets.asSequence()
            .filterNot { it.isRemoved }
            .mapNotNull { it.location }
            .toList()
        val staleLocations: MutableSet<EtsStmt> = identitySet()
        removedTargets.asSequence()
            .mapNotNull { it.location }
            .filter { removed -> liveLocations.none { it === removed } }
            .forEach(staleLocations::add)

        staleLocations.forEach { location ->
            if (distanceCalculator.removeTargetFromCache(location)) {
                cachedTargetLocations.remove(location)
                cacheInvalidations++
            }
        }
        observedTargets.removeAll(removedTargets.toSet())
    }

    private fun calculateEvidence(
        delegate: InterprocDistanceCalculator<EtsMethod, EtsStmt>,
        target: EtsStmt,
        currentStatement: EtsStmt,
        callStack: UCallStack<EtsMethod, EtsStmt>,
    ): ReachabilityEvidence {
        distanceEvaluations++
        val startedAt = nanoTime()
        val evidence = try {
            if (!hasKnownLocations(target, currentStatement, callStack)) {
                ReachabilityEvidence.INDETERMINATE
            } else {
                val distance = runCatching {
                    delegate.calculateDistance(currentStatement, callStack)
                }.getOrNull()

                when {
                    distance == null -> ReachabilityEvidence.INDETERMINATE
                    !distance.isInfinite -> ReachabilityEvidence.REACHABLE
                    provesUnreachableWithoutCalls(target, currentStatement, callStack) ->
                        ReachabilityEvidence.PROVEN_UNREACHABLE
                    else -> ReachabilityEvidence.INDETERMINATE
                }
            }
        } finally {
            distanceEvaluationNanos += (nanoTime() - startedAt).coerceAtLeast(0)
        }

        when (evidence) {
            ReachabilityEvidence.PROVEN_UNREACHABLE -> provenUnreachableDistances++
            ReachabilityEvidence.INDETERMINATE -> indeterminateDistances++
            ReachabilityEvidence.REACHABLE -> Unit
        }
        return evidence
    }

    private fun hasKnownLocations(
        target: EtsStmt,
        currentStatement: EtsStmt,
        callStack: UCallStack<EtsMethod, EtsStmt>,
    ): Boolean {
        if (callStack.isEmpty() || !isKnownStatement(target) || !isKnownStatement(currentStatement)) {
            return false
        }
        if (applicationGraph.methodOf(currentStatement) != callStack.lastMethod()) {
            return false
        }

        for (frameIndex in 1..callStack.lastIndex) {
            val returnSite = callStack[frameIndex].returnSite ?: return false
            val callerMethod = callStack[frameIndex - 1].method
            if (!isKnownStatement(returnSite) || applicationGraph.methodOf(returnSite) != callerMethod) {
                return false
            }
        }
        return true
    }

    /**
     * Turns infinity into a pruning proof only when the static CFG fully describes every remaining continuation.
     * A reachable call expression makes the result indeterminate because TS call resolution is intentionally
     * incomplete; this prevents an under-approximated call graph from deleting a feasible target path.
     */
    private fun provesUnreachableWithoutCalls(
        target: EtsStmt,
        currentStatement: EtsStmt,
        callStack: UCallStack<EtsMethod, EtsStmt>,
    ): Boolean {
        var continuation = currentStatement

        for (frameIndex in callStack.lastIndex downTo 0) {
            val method = callStack[frameIndex].method
            val reachableFrame = collectReachableFrame(method, continuation) ?: return false

            if (reachableFrame.statements.any { it === target }) {
                return false
            }
            for (statement in reachableFrame.statements) {
                val containsCall = runCatching { statement.callExpr != null }.getOrNull() ?: return false
                if (containsCall) {
                    return false
                }
            }

            if (!reachableFrame.reachesExit || frameIndex == 0) {
                return true
            }
            continuation = callStack[frameIndex].returnSite ?: return false
        }

        return true
    }

    private fun collectReachableFrame(method: EtsMethod, start: EtsStmt): ReachableFrame? {
        val knownStatements = knownStatements(method)
        if (knownStatements.none { it === start }) {
            return null
        }

        val visited: MutableSet<EtsStmt> = identitySet()
        val queue = ArrayDeque<EtsStmt>()
        visited.add(start)
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val successors = runCatching { applicationGraph.successors(current).toList() }.getOrNull() ?: return null
            for (successor in successors) {
                if (knownStatements.none { it === successor } || applicationGraph.methodOf(successor) != method) {
                    return null
                }
                if (visited.add(successor)) {
                    queue.addLast(successor)
                }
            }
        }

        val exits = runCatching { applicationGraph.exitPoints(method).toList() }.getOrNull() ?: return null
        if (exits.isEmpty() || exits.any { exit -> knownStatements.none { it === exit } }) {
            return null
        }
        return ReachableFrame(
            statements = visited,
            reachesExit = exits.any { exit -> visited.any { it === exit } },
        )
    }

    private fun isKnownStatement(statement: EtsStmt): Boolean {
        val method = runCatching { applicationGraph.methodOf(statement) }.getOrNull() ?: return false
        return knownStatements(method).any { it === statement }
    }

    private fun knownStatements(method: EtsMethod): List<EtsStmt> =
        knownStatementsByMethod.getOrPut(method) {
            runCatching { applicationGraph.statementsOf(method).toList() }.getOrDefault(emptyList())
        }

    private companion object {
        fun <T> identitySet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())
    }
}
