package org.usvm

interface UPathSelector<State> : AutoCloseable {
    fun isEmpty(): Boolean

    fun peekAndUpdate(step: (State) -> StepResult<State>)

    fun add(producedStates: Sequence<State>)

    fun queue(): List<State>
}