package org.usvm.statistics.distances

import org.usvm.UCallStack

fun interface StaticTargetsDistanceCalculator<Method, Statement, out Distance> {
    fun calculateDistance(currentStatement: Statement, callStack: UCallStack<Method, Statement>): Distance
}

class DynamicTargetsShortestDistanceCalculator<Method, Statement, Distance>(
    private val getDistanceCalculator: (Method, Statement) -> StaticTargetsDistanceCalculator<Method, Statement, Distance>
) {
    private val calculatorsByTarget = HashMap<Pair<Method, Statement>, StaticTargetsDistanceCalculator<Method, Statement, Distance>>()

    // TODO: use
    fun removeTargetFromCache(target: Pair<Method, Statement>): Boolean {
        return calculatorsByTarget.remove(target) != null
    }

    fun calculateDistance(
        currentStatement: Statement,
        callStack: UCallStack<Method, Statement>,
        target: Pair<Method, Statement>
    ): Distance {
        val calculator = calculatorsByTarget.computeIfAbsent(target) { getDistanceCalculator(it.first, it.second) }
        return calculator.calculateDistance(currentStatement, callStack)
    }
}
