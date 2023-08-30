package org.usvm.statistics.distances

import org.usvm.UCallStack
import org.usvm.statistics.ApplicationGraph

enum class ReachabilityKind {
    LOCAL,
    UP_STACK,
    DOWN_STACK,
    NONE
}

// TODO: add more information about the path
// TODO: add new targets according to the path?
data class InterprocDistance(val distance: UInt, val reachabilityKind: ReachabilityKind) {
    val isUnreachable = reachabilityKind == ReachabilityKind.NONE
}

// TODO: calculate distance in blocks??
// TODO: give priority to paths without calls
internal class InterprocDistanceCalculator<Method, Statement>(
    private val targetLocation: Pair<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val getCfgDistance: (Method, Statement, Statement) -> UInt,
    private val getCfgDistanceToExitPoint: (Method, Statement) -> UInt,
    private val checkReachabilityInCallGraph: (Method, Method) -> Boolean
) : StaticTargetsDistanceCalculator<Method, Statement, InterprocDistance> {

    private val frameDistanceCache = HashMap<Method, HashMap<Statement, UInt>>()

    private fun calculateFrameDistance(method: Method, statement: Statement): Pair<UInt, Boolean> {
        if (method == targetLocation.first) {
            val localDistance = getCfgDistance(method, statement, targetLocation.second)
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

            val distanceToCall = getCfgDistance(method, statement, statementOfMethod)
            if (distanceToCall >= minDistanceToCall) {
                continue
            }

            if (applicationGraph.callees(statementOfMethod).any { checkReachabilityInCallGraph(it, targetLocation.first) }) {
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
                return InterprocDistance(getCfgDistanceToExitPoint(lastMethod, currentStatement), ReachabilityKind.DOWN_STACK)
            }

            statementOnCallStack = returnSite
        }

        return InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
    }
}
