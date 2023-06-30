package org.usvm.ps

import org.usvm.UState
import kotlin.math.min

/**
 * [StateWeighter] implementation which weights states by their application graph
 * distance to specified targets.
 *
 * Distances in graph remain the same, only the targets can change, so the local CFG distances are
 * cached while the targets of the method remain the same.
 *
 * @param targets initial collection of targets.
 * @param getCfgDistance function with the following signature:
 * (method, stmtFrom, stmtTo) -> shortest CFG distance from stmtFrom to stmtTo.
 * @param getCfgDistanceToExitPoint function with the following signature:
 * (method, stmt) -> shortest CFG distance from stmt to any of method's exit points.
 */
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

        // minDistanceToTarget(F) =
        //  min(
        //      min distance from F to target in current frame (if there are any),
        //      min distance from F to return point R of current frame + minDistanceToTarget(point in prev frame where R returns)
        //  )
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
                if (minDistanceToReturn == UInt.MAX_VALUE || currentMinDistanceToTarget == UInt.MAX_VALUE)
                    UInt.MAX_VALUE
                else
                    currentMinDistanceToTarget + minDistanceToReturn

            currentMinDistanceToTarget = min(minDistanceToTargetInPreviousFrames, minDistanceToTargetInCurrentFrame)
        }

        return currentMinDistanceToTarget
    }
}
