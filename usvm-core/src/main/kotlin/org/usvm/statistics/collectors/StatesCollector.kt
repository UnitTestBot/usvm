package org.usvm.statistics.collectors

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.usvm.statistics.UMachineObserver

/**
 * Interface for [UMachineObserver]s which are able to
 * collect states.
 */
interface StatesCollector<State> {
    val count: Int
    fun addState(state: State)
}

class FlowStatesCollector<State> : StatesCollector<State> {
    private var statesCount: Int = 0
    override val count: Int
        get() = statesCount

    private val flow = MutableSharedFlow<State>()
    val collectedStatesFlow: Flow<State> = flow

    override fun addState(state: State) = runBlocking {
        statesCount++
        flow.emit(state)
    }
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
