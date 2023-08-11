package org.usvm.statistics

import org.usvm.UState

class TerminatedStateRemover<State : UState<*, *, *, *, *, State>>(
) : UMachineObserver<State> {
    override fun onStateTerminated(state: State) {
        state.pathLocation.states.remove(state)
    }
}
