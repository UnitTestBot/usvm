package org.usvm

interface UPathSelector<State> : AutoCloseable {
    fun isEmpty(): Boolean

    fun peek(): State

    fun terminate(state: State)

    fun add(sourceState: State?, producedStates: Collection<State>)

    fun queue(): List<State>
}