package org.usvm

interface UPathSelector<State> {
    fun isEmpty(): Boolean

    fun peek(): State

    fun terminate(state: State)

    fun add(producedStates: Sequence<State>)

    fun update(state: State)
}