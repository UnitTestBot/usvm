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

class InterprocDistance(val distance: UInt, reachabilityKind: ReachabilityKind) {

    val isInfinite = distance == UInt.MAX_VALUE
    val reachabilityKind = if (distance != UInt.MAX_VALUE) reachabilityKind else ReachabilityKind.NONE

    override fun equals(other: Any?): Boolean {
        if (other !is InterprocDistance) {
            return false
        }
        return other.distance == distance && other.reachabilityKind == reachabilityKind
    }

    override fun hashCode(): Int = distance.toInt() * 31 + reachabilityKind.hashCode()

    override fun toString(): String = "InterprocDistance($distance, $reachabilityKind)"
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
class InterprocDistanceCalculator<Method, Statement>(
    private val targetLocation: Statement,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val cfgStatistics: CfgStatistics<Method, Statement>,
    private val callGraphStatistics: CallGraphStatistics<Method>
) : DistanceCalculator<Method, Statement, InterprocDistance> {

    private val frameDistanceCache = hashMapOf<Method, HashMap<Statement, UInt>>()

    private fun calculateFrameDistance(method: Method, statement: Statement): InterprocDistance {
        val targetLocationMethod = applicationGraph.methodOf(targetLocation)
        if (method == targetLocationMethod) {
            val localDistance = cfgStatistics.getShortestDistance(method, statement, targetLocation)
            if (localDistance != UInt.MAX_VALUE) {
                return InterprocDistance(localDistance, ReachabilityKind.LOCAL)
            }
        }

        val cached = frameDistanceCache[method]?.get(statement)
        if (cached != null) {
            return InterprocDistance(cached, ReachabilityKind.UP_STACK)
        }

        var minDistanceToCall = UInt.MAX_VALUE
        for (statementOfMethod in applicationGraph.statementsOf(method)) {
            val callees = applicationGraph.callees(statementOfMethod)

            if (!callees.any()) {
                continue
            }

            val distanceToCall = cfgStatistics.getShortestDistance(method, statement, statementOfMethod)
            if (distanceToCall >= minDistanceToCall) {
                continue
            }

            if (callees.any { callGraphStatistics.checkReachability(it, targetLocationMethod) }) {
                minDistanceToCall = distanceToCall
            }
        }

        if (minDistanceToCall != UInt.MAX_VALUE) {
            frameDistanceCache.computeIfAbsent(method) { hashMapOf() }[statement] = minDistanceToCall
        }

        return InterprocDistance(minDistanceToCall, ReachabilityKind.UP_STACK)
    }

    override fun calculateDistance(
        currentStatement: Statement,
        callStack: UCallStack<Method, Statement>
    ): InterprocDistance {
        if (callStack.isEmpty()) {
            return InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
        }

        val lastMethod = callStack.lastMethod()
        val lastFrameDistance = calculateFrameDistance(lastMethod, currentStatement)

        if (!lastFrameDistance.isInfinite) {
            return lastFrameDistance
        }

        var statementOnCallStack = callStack.last().returnSite

        for (i in callStack.size - 2 downTo 0) {
            val (methodOnCallStack, returnSite) = callStack[i]
            checkNotNull(statementOnCallStack) { "Not first call stack frame had null return site" }

            val successors = applicationGraph.successors(statementOnCallStack)
            val hasReachableSuccessors =
                !calculateFrameDistance(methodOnCallStack, statementOnCallStack).isInfinite || // TODO seems like it is something JcInterpreter specific
                        successors.any { !calculateFrameDistance(methodOnCallStack, it).isInfinite }

            if (hasReachableSuccessors) {
                val distanceToExit = cfgStatistics.getShortestDistanceToExit(lastMethod, currentStatement)
                return InterprocDistance(distanceToExit, ReachabilityKind.DOWN_STACK)
            }

            statementOnCallStack = returnSite
        }

        return InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
    }
}
