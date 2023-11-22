package org.usvm.statistics.collectors

import org.usvm.UState
import org.usvm.statistics.UMachineObserver

/**
 * [StatesCollector] implementation collecting only those states which have reached
 * any terminal targets.
 */
class TargetsReachedStatesCollector<State : UState<*, *, *, *, *, State>>(
    val statesCollector: StatesCollector<State>
) : UMachineObserver<State> {
    // TODO probably this should be called not only for terminated states
    //      Also, we should process more carefully clone operation for the states
    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (state.targets.reachedTerminal.isNotEmpty()) {
            statesCollector.addState(state)
        }
    }
}
