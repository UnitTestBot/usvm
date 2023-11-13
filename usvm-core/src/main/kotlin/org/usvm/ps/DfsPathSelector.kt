package org.usvm.ps

import org.usvm.UPathSelector

class DfsPathSelector<State> : UPathSelector<State> {
    private val stack = ArrayDeque<State>()

    override fun isEmpty() = stack.isEmpty()

    override fun peek() = stack.last()
    override fun update(state: State): Boolean {
        // nothing to do
        return true
    }

    override fun remove(state: State) {
        when (state) {
            stack.last() -> stack.removeLast() // fast remove from the tail
            stack.first() -> stack.removeFirst() // fast remove from the head
            else -> stack.remove(state)
        }
    }

    override fun add(state: State): Boolean {
        return stack.add(state)
    }
}
