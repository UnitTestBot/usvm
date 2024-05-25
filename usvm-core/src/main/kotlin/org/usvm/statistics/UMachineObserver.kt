package org.usvm.statistics

import org.usvm.BannedState

/**
 * Symbolic machine events observer.
 */
interface UMachineObserver<State> {

    /**
     * Called when the execution of the state is terminated (by exception or return).
     */
    fun onStateTerminated(state: State, stateReachable: Boolean) { }

    /**
     * Called on each symbolic execution step. If the state has forked, [forks] are not empty.
     */
    fun onState(parent: State, forks: Sequence<State>) { }

    fun onStateDeath(state: State, bannedStates: Sequence<BannedState>) { }
}

class CompositeUMachineObserver<State>(
    private val observers: List<UMachineObserver<State>>
) : UMachineObserver<State> {
    constructor(vararg observers: UMachineObserver<State>) : this(observers.toList())

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        observers.forEach { it.onStateTerminated(state, stateReachable) }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        observers.forEach { it.onState(parent, forks) }
    }

    override fun onStateDeath(state: State, bannedStates: Sequence<BannedState>) {
        observers.forEach { it.onStateDeath(state, bannedStates) }
    }
}
