package org.usvm.statistics.distances

import org.usvm.UCallStack

/**
 * @see calculateDistance
 */
fun interface StaticTargetsDistanceCalculator<Method, Statement, out Distance> {

    /**
     * Calculate distance from location represented by [currentStatement] and [callStack] to
     * some predefined targets.
     */
    fun calculateDistance(currentStatement: Statement, callStack: UCallStack<Method, Statement>): Distance
}

/**
 * Dynamically accumulates multiple [StaticTargetsDistanceCalculator] by their targets allowing
 * to calculate distances to arbitrary targets.
 */
class DynamicTargetsShortestDistanceCalculator<Method, Statement, Distance>(
    private val getDistanceCalculator: (Method, Statement) -> StaticTargetsDistanceCalculator<Method, Statement, Distance>
) {
    private val calculatorsByTarget = HashMap<Pair<Method, Statement>, StaticTargetsDistanceCalculator<Method, Statement, Distance>>()

    // TODO: use
    fun removeTargetFromCache(target: Pair<Method, Statement>): Boolean {
        return calculatorsByTarget.remove(target) != null
    }

    /**
     * Calculate distance from location represented by [currentStatement] and [callStack] to the [target].
     */
    fun calculateDistance(
        currentStatement: Statement,
        callStack: UCallStack<Method, Statement>,
        target: Pair<Method, Statement>
    ): Distance {
        val calculator = calculatorsByTarget.computeIfAbsent(target) { getDistanceCalculator(it.first, it.second) }
        return calculator.calculateDistance(currentStatement, callStack)
    }
}
