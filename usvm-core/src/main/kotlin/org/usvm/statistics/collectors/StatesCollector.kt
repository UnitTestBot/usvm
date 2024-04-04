package org.usvm.statistics.collectors

import org.usvm.statistics.UMachineObserver

/**
 * Interface for [UMachineObserver]s which are able to
 * collect states.
 */
interface StatesCollector<State> {
    val count: Int
    fun addState(state: State)
}

class ListStatesCollector<State> : StatesCollector<State> {
    /**
     * Current list of collected states.
     */
    private val states = mutableListOf<State>()
    val collectedStates: List<State> = states

    override val count: Int
        get() = states.size

    override fun addState(state: State) {
        states.add(state)
    }
}
