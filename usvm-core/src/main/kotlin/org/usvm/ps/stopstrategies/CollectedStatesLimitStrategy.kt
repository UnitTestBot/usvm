package org.usvm.ps.stopstrategies

class CollectedStatesLimitStrategy(
    val statesLimit: Int = STATES_LIMIT
) : StoppingStrategy {
    var statesCount = 0

    override fun shouldStop(): Boolean = statesCount > statesLimit

    fun incrementStatesCount() {
        statesCount++
    }

    companion object {
        const val STATES_LIMIT = 20
    }
}