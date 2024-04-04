package org.usvm.statistics.collectors

import org.usvm.UState
import org.usvm.statistics.UMachineObserver

/**
 * [StatesCollector] implementation collecting all states.
 */
class AllStatesCollector<State : UState<*, *, *, *, *, State>>(
    private val statesCollector: StatesCollector<State>,
) : UMachineObserver<State> {
    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (stateReachable) {
            statesCollector.addState(state)
        }
    }
}
