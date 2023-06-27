package org.usvm.stopstrategies

/**
 * [StopStrategy] which stops when [statesLimit] state number is reached.
 */
class CollectedStatesLimitStrategy(
    private val statesLimit: Int = STATES_LIMIT
) : StopStrategy {
    private var statesCount = 0

    override fun shouldStop(): Boolean = statesCount > statesLimit

    fun incrementStatesCount() {
        statesCount++
    }

    companion object {
        const val STATES_LIMIT = 20
    }
}
