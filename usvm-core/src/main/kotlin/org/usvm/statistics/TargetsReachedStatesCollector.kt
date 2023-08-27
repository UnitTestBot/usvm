package org.usvm.statistics

import org.usvm.UState

class TargetsReachedStatesCollector<State : UState<*, *, *, *, *, *, State>> : UMachineObserver<State> {
    private val mutableCollectedStates = mutableListOf<State>()
    val collectedStates: List<State> = mutableCollectedStates

    override fun onStateTerminated(state: State) {
        if (state.reachedSinks.isNotEmpty()) {
            mutableCollectedStates.add(state)
        }
    }
}
