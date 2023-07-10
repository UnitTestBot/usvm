package org.usvm.stopstrategies

/**
 * A stop strategy that checks how many steps were made since the last collected states.
 */
class StepsFromLastCoveredStopStrategy(
    private val limit: ULong,
    getCollectedStatesCount: (() -> Int)?,
) : StopStrategy {
    private val collectedStateCount: () -> Int

    init {
        collectedStateCount = requireNotNull(getCollectedStatesCount) {
            "You must specify a provider of the number of collected states to be able to use this stop strategy"
        }
    }

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