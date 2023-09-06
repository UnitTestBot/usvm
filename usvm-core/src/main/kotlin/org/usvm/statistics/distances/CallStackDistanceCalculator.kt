package org.usvm.statistics.distances

import org.usvm.Location
import kotlin.math.min
import org.usvm.UCallStack

/**
 * Calculates shortest distances from location (represented as statement and call stack) to the set of targets
 * considering only CFGs of methods on the call stack.
 *
 * Distances in graph remain the same, only the targets can change, so the local CFG distances are
 * cached while the targets of the method remain the same.
 *
 * @param targets initial collection of targets.
 * @param cfgStatistics [CfgStatistics] instance used to calculate local distances on each frame.
 */
class CallStackDistanceCalculator<Method, Statement>(
    targets: Collection<Location<Method, Statement>>,
    private val cfgStatistics: CfgStatistics<Method, Statement>
) : DistanceCalculator<Method, Statement, UInt> {

    // TODO: optimize for single target case
    private val targetsByMethod = HashMap<Method, HashSet<Statement>>()
    private val minLocalDistanceToTargetCache = HashMap<Method, HashMap<Statement, UInt>>()

    init {
        for ((method, stmt) in targets) {
            val statements = targetsByMethod.computeIfAbsent(method) { hashSetOf() }
            statements.add(stmt)
        }
    }

    private fun getMinDistanceToTargetInCurrentFrame(method: Method, statement: Statement): UInt {
        return minLocalDistanceToTargetCache.computeIfAbsent(method) { hashMapOf() }
            .computeIfAbsent(statement) {
                targetsByMethod[method]?.minOfOrNull { cfgStatistics.getShortestDistance(method, statement, it) } ?: UInt.MAX_VALUE
            }
    }

    fun addTarget(method: Method, statement: Statement): Boolean {
        val statements = targetsByMethod.computeIfAbsent(method) { hashSetOf() }
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

    override fun calculateDistance(currentStatement: Statement, callStack: UCallStack<Method, Statement>): UInt {
        var currentMinDistanceToTarget = UInt.MAX_VALUE
        val callStackArray = callStack.toTypedArray()

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

            val minDistanceToReturn = cfgStatistics.getShortestDistanceToExit(method, locationInMethod)
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
