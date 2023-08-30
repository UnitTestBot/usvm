package org.usvm.statistics.collectors

import org.usvm.UState

class TargetsReachedStatesCollector<State : UState<*, *, *, *, *, *, State>> : StatesCollector<State> {
    private val mutableCollectedStates = mutableListOf<State>()
    override val collectedStates: List<State> = mutableCollectedStates

    override fun onStateTerminated(state: State) {
        if (state.reachedSinks.isNotEmpty()) {
            mutableCollectedStates.add(state)
        }
    }
}
