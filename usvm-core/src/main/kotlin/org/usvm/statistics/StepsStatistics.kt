package org.usvm.statistics

import org.usvm.UState


/**
 * [UMachineObserver] which tracks amount of steps taken
 *
 */
class StepsStatistics<State : UState<*, *, *, *, *, *>> : UMachineObserver<State> {

    private var steps = 0
    override fun onState(parent: State, forks: Sequence<State>) {
        steps += 1
    }

    fun getStepsCount() = steps
}
