package org.usvm

interface UPathSelector<State> {
    fun isEmpty(): Boolean

    /**
     * @return the next state in the path selector.
     */
    fun peek(): State

    /**
     * Removes the [state] from the path selector.
     */
    fun remove(state: State)

    /**
     * Adds [states] to the path selector.
     */
    fun add(states: Sequence<State>)

    /**
     * Updates the internal priority of the [state].
     */
    fun update(state: State)
}