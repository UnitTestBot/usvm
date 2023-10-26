package org.usvm.statistics.collectors

import org.usvm.UState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [StatesCollector] implementation collecting only those states which have reached
 * any terminal targets.
 */
class TargetsReachedStatesCollector<State : UState<*, *, *, *, *, State>> : StatesCollector<State> {
    private val mutableCollectedStates = mutableListOf<State>()
    override val collectedStates: List<State> = mutableCollectedStates

    // TODO probably this should be called not only for terminated states
    //      Also, we should process more carefully clone operation for the states
    override fun onStateTerminated(state: State, stateReachable: Boolean, isConsumed: AtomicBoolean) {
        if (state.reachedTerminalTargets.isNotEmpty()) {
            mutableCollectedStates.add(state)
        }
    }
}
