package org.usvm.statistics.collectors

import org.usvm.UState

/**
 * [StatesCollector] implementation collecting all states.
 */
class AllStatesCollector<State : UState<*, *, *, *, *, State>> : StatesCollector<State> {
    private val mutableCollectedStates = mutableListOf<State>()
    override val collectedStates: List<State> = mutableCollectedStates

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (stateReachable) {
            mutableCollectedStates.add(state)
        }
    }
}
