package org.usvm.stopstrategies

import org.usvm.statistics.StepsStatistics

/**
 * A stop strategy that checks how many steps were made since the last collected states.
 */
class StepsFromLastCoveredStopStrategy(
    private val limit: ULong,
    private val collectedStateCount: () -> Int,
    private val stepsStatistics: StepsStatistics<*, *>
) : StopStrategy {
    private var stepsMadeOnLastCollected = 0UL
    private var lastStatesCounter = collectedStateCount()

    override fun shouldStop(): Boolean {
        val collectedStates = collectedStateCount()

        if (collectedStates > lastStatesCounter) {
            stepsMadeOnLastCollected = stepsStatistics.totalSteps
            lastStatesCounter = collectedStates
        }

        return (stepsStatistics.totalSteps - stepsMadeOnLastCollected) > limit
    }
}
