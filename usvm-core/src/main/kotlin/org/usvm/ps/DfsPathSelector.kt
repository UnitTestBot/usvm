package org.usvm.ps

import org.usvm.UPathSelector

class DfsPathSelector<State> : UPathSelector<State> {
    private val queue = ArrayDeque<State>()
    override fun isEmpty() =
        queue.isEmpty()

    override fun peek() =
        queue.first()


    override fun terminate() {
        queue.removeFirst()
    }

    override fun add(states: Sequence<State>) {
        queue.addAll(states)
    }
}