package org.usvm.ps

import org.usvm.UState

// TODO is it necessary to have all these generics here?
interface PathSelector<State : UState<*, *, Method, Statement>, Method, Statement> : AutoCloseable {
    fun peek(): State

    fun update(sourceState: State, producedStates: Iterable<State>)

    fun isEmpty(): Boolean

    fun terminate(state: State)

    fun queue(): Iterable<State>
}