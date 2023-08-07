package org.usvm.ps

import org.usvm.UPathSelector

class BfsPathSelector<State> : UPathSelector<State> {
    private val queue = ArrayDeque<State>()

    override fun isEmpty() = queue.isEmpty()

    override fun peek() = queue.first()

    override fun update(state: State) {
        if (state === queue.first()) {
            queue.removeFirst()
            queue.addLast(state)
        }
    }

    override fun add(states: Collection<State>) {
        queue.addAll(states)
    }

    override fun remove(state: State) {
        when (state) {
            queue.last() -> queue.removeLast() // fast remove from the tail
            queue.first() -> queue.removeFirst() // fast remove from the head
            else -> queue.remove(state)
        }
    }
}
