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
    fun add(states: Collection<State>)

    /**
     * Updates the internal priority of the [state].
     */
    fun update(state: State)

    /**
     * Removes and yields every queued state after execution has stopped.
     * Wrappers with scheduling or merging side effects must drain their children directly.
     * Each state is removed before it is yielded; the sequence must be consumed once.
     */
    fun drain(): Sequence<State> = sequence {
        while (!isEmpty()) {
            val state = peek()
            remove(state)
            yield(state)
        }
    }
}
