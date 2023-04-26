package org.usvm

interface UPathSelector<State> {
    fun isEmpty(): Boolean

    fun peek(): State

    fun terminate()

    fun add(states: Sequence<State>)
}