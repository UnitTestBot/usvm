package org.usvm.statistics.distances

import org.usvm.UCallStack
import org.usvm.statistics.ApplicationGraph

/**
 * Kind of target reachability in application graph.
 */
enum class ReachabilityKind {
    /**
     * Target is located in the same method and is locally reachable.
     */
    LOCAL,

    /**
     * Target is reachable from some method which can be called later.
     */
    UP_STACK,
    /**
     * Target is reachable from some method on the call stack after returning to it.
     */
    DOWN_STACK,
    /**
     * Target is unreachable.
     */
    NONE
}

data class InterprocDistance(val distance: UInt, val reachabilityKind: ReachabilityKind) {
    val isUnreachable = reachabilityKind == ReachabilityKind.NONE
}

/**
 * Calculates shortest distances from location (represented as statement and call stack) to the set of targets
 * considering call graph reachability.
 *
 * @param targetLocation target to calculate distance to.
 * @param applicationGraph application graph to calculate distances on.
 * @param cfgStatistics [CfgStatistics] instance used to calculate local distances.
 * @param callGraphStatistics [CallGraphStatistics] instance used to check call graph reachability.
 */
// TODO: calculate distance in blocks??
// TODO: give priority to paths without calls
// TODO: add new targets according to the path?
internal class InterprocDistanceCalculator<Method, Statement>(
    private val targetLocation: Pair<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val cfgStatistics: CfgStatistics<Method, Statement>,
    private val callGraphStatistics: CallGraphStatistics<Method>
) : DistanceCalculator<Method, Statement, InterprocDistance> {

    private val frameDistanceCache = HashMap<Method, HashMap<Statement, UInt>>()

    private fun calculateFrameDistance(method: Method, statement: Statement): Pair<UInt, Boolean> {
        if (method == targetLocation.first) {
            val localDistance = cfgStatistics.getShortestDistance(method, statement, targetLocation.second)
            if (localDistance != UInt.MAX_VALUE) {
                return localDistance to true
            }
        }

        val cached = frameDistanceCache[method]?.get(statement)
        if (cached != null) {
            return cached to false
        }

        var minDistanceToCall = UInt.MAX_VALUE
        for (statementOfMethod in applicationGraph.statementsOf(method)) {
            if (!applicationGraph.callees(statementOfMethod).any()) {
                continue
            }

            val distanceToCall = cfgStatistics.getShortestDistance(method, statement, statementOfMethod)
            if (distanceToCall >= minDistanceToCall) {
                continue
            }

            if (applicationGraph.callees(statementOfMethod).any { callGraphStatistics.checkReachability(it, targetLocation.first) }) {
                minDistanceToCall = distanceToCall
            }
        }

        if (minDistanceToCall != UInt.MAX_VALUE) {
            frameDistanceCache.computeIfAbsent(method) { HashMap() }[statement] = minDistanceToCall
        }
        return minDistanceToCall to false
    }

    override fun calculateDistance(
        currentStatement: Statement,
        callStack: UCallStack<Method, Statement>
    ): InterprocDistance {
        val lastMethod = callStack.lastMethod()
        val (lastFrameDistance, isLocal) = calculateFrameDistance(lastMethod, currentStatement)
        if (lastFrameDistance != UInt.MAX_VALUE) {
            return InterprocDistance(lastFrameDistance, if (isLocal) ReachabilityKind.LOCAL else ReachabilityKind.UP_STACK)
        }

        var statementOnCallStack = callStack.last().returnSite
        for ((methodOnCallStack, returnSite) in callStack.reversed().drop(1)) {
            checkNotNull(statementOnCallStack) { "Not first call stack frame had null return site" }

            if (applicationGraph.successors(statementOnCallStack).any { calculateFrameDistance(methodOnCallStack, it).first != UInt.MAX_VALUE }) {
                return InterprocDistance(cfgStatistics.getShortestDistanceToExit(lastMethod, currentStatement), ReachabilityKind.DOWN_STACK)
            }

            statementOnCallStack = returnSite
        }

        return InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
    }
}
