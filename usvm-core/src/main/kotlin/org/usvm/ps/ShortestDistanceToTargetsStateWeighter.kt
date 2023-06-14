package org.usvm.ps

import org.usvm.UState
import kotlin.math.min

class ShortestDistanceToTargetsStateWeighter<Method, Statement, State : UState<*, *, Method, Statement>>(
    targets: Collection<Pair<Method, Statement>>,
    private val getCfgDistance: (Method, Statement, Statement) -> UInt,
    private val getCfgDistanceToExitPoint: (Method, Statement) -> UInt
) : StateWeighter<State, UInt> {

    private val targetsByMethod = HashMap<Method, HashSet<Statement>>()
    private val minLocalDistanceToTargetCache = HashMap<Method, HashMap<Statement, UInt>>()

    init {
        for ((method, stmt) in targets) {
            val statements = targetsByMethod.computeIfAbsent(method) { HashSet() }
            statements.add(stmt)
        }
    }

    private fun getMinDistanceToTargetInCurrentFrame(method: Method, statement: Statement): UInt {
        return minLocalDistanceToTargetCache.computeIfAbsent(method) { HashMap() }
            .computeIfAbsent(statement) { targetsByMethod[method]?.minOfOrNull { getCfgDistance(method, statement, it) } ?: UInt.MAX_VALUE }
    }

    fun addTarget(method: Method, statement: Statement): Boolean {
        val statements = targetsByMethod.computeIfAbsent(method) { HashSet() }
        val wasAdded = statements.add(statement)
        if (wasAdded) {
            minLocalDistanceToTargetCache.remove(method)
        }
        return wasAdded
    }

    fun removeTarget(method: Method, statement: Statement): Boolean {
        val statements = targetsByMethod[method]
        val wasRemoved = statements?.remove(statement) ?: false
        if (wasRemoved) {
            minLocalDistanceToTargetCache.remove(method)
        }
        return wasRemoved
    }

    override fun weight(state: State): UInt {
        val currentStatement = state.currentStatement ?: return 0u

        var currentMinDistanceToTarget = UInt.MAX_VALUE

        val callStackArray = state.callStack.toTypedArray()

        for (i in callStackArray.indices) {
            val method = callStackArray[i].method
            val locationInMethod =
                if (i < callStackArray.size - 1) {
                    val returnSite = callStackArray[i + 1].returnSite
                    checkNotNull(returnSite) { "Not first call stack frame had null return site" }
                } else currentStatement

            val minDistanceToReturn = getCfgDistanceToExitPoint(method, locationInMethod)
            val minDistanceToTargetInCurrentFrame = getMinDistanceToTargetInCurrentFrame(method, locationInMethod)

            val minDistanceToTargetInPreviousFrames =
                if (minDistanceToReturn == UInt.MAX_VALUE || currentMinDistanceToTarget == UInt.MAX_VALUE) UInt.MAX_VALUE else currentMinDistanceToTarget + minDistanceToReturn

            currentMinDistanceToTarget = min(minDistanceToTargetInPreviousFrames, minDistanceToTargetInCurrentFrame)
        }

        return currentMinDistanceToTarget
    }
}
