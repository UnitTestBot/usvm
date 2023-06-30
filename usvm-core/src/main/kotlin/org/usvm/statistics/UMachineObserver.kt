package org.usvm.statistics

/**
 * Symbolic machine events observer.
 */
interface UMachineObserver<State> {

    /**
     * Called when the execution of the state is terminated (by exception or return).
     */
    fun onStateTerminated(state: State) { }

    /**
     * Called on each symbolic execution step. If the state has forked, [forks] are not empty.
     */
    fun onState(parent: State, forks: Sequence<State>) { }
}

class CompositeUMachineObserver<State>(private val observers: List<UMachineObserver<State>>) : UMachineObserver<State> {
    constructor(vararg observers: UMachineObserver<State>) : this(observers.toList())

    override fun onStateTerminated(state: State) {
        observers.forEach { it.onStateTerminated(state) }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        observers.forEach { it.onState(parent, forks) }
    }
}
