package org.usvm.stopstrategies

/**
 * A stop strategy that checks how many steps were made since the last collected states.
 */
class StepsFromLastCoveredStopStrategy(
    private val limit: ULong,
    private val collectedStateCount: () -> Int,
) : StopStrategy {
    private var counter = 0UL
    private var lastStatesCounter = collectedStateCount()

    override fun shouldStop(): Boolean {
        val collectedStates = collectedStateCount()

        if (collectedStates > lastStatesCounter) {
            counter = 0UL
            lastStatesCounter = collectedStates
        }

        return counter++ > limit
    }
}