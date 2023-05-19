package org.usvm.ps

import org.usvm.UPathSelector

class DfsPathSelector<State> : UPathSelector<State> {
    private val stack = ArrayDeque<State>()

    override fun isEmpty() = stack.isEmpty()

    override fun peek() = stack.last()
    override fun update(state: State) {
        // nothing to do
    }

    override fun remove(state: State) {
        when (state) {
            stack.last() -> stack.removeLast() // fast remove from the tail
            stack.first() -> stack.removeFirst() // fast remove from the head
            else -> stack.remove(state)
        }
    }

    override fun add(states: Collection<State>) {
        stack.addAll(states)
    }
}
