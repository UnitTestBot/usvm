package org.usvm.statistics.distances

import org.usvm.Location
import org.usvm.UCallStack

/**
 * @see calculateDistance
 */
fun interface DistanceCalculator<Method, Statement, out Distance> {

    /**
     * Calculate distance from location represented by [currentStatement] and [callStack] to
     * some predefined targets.
     */
    fun calculateDistance(currentStatement: Statement, callStack: UCallStack<Method, Statement>): Distance
}

/**
 * Dynamically accumulates multiple [DistanceCalculator] by their targets allowing
 * to calculate distances to arbitrary targets.
 */
class MultiTargetDistanceCalculator<Method, Statement, Distance>(
    private val getDistanceCalculator: (Location<Method, Statement>) -> DistanceCalculator<Method, Statement, Distance>
) {
    private val calculatorsByTarget = HashMap<Location<Method, Statement>, DistanceCalculator<Method, Statement, Distance>>()

    // TODO: use
    fun removeTargetFromCache(target: Location<Method, Statement>): Boolean {
        return calculatorsByTarget.remove(target) != null
    }

    /**
     * Calculate distance from location represented by [currentStatement] and [callStack] to the [target].
     */
    fun calculateDistance(
        currentStatement: Statement,
        callStack: UCallStack<Method, Statement>,
        target: Location<Method, Statement>
    ): Distance {
        val calculator = calculatorsByTarget.computeIfAbsent(target) { getDistanceCalculator(it) }
        return calculator.calculateDistance(currentStatement, callStack)
    }
}
