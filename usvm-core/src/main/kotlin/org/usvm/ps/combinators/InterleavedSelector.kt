package org.usvm.ps.combinators

import org.usvm.UPathSelector

class InterleavedSelector<State>(
    val selectors: List<UPathSelector<State>>,
    val independent: Boolean = true,
) : UPathSelector<State> {
    constructor(vararg selectors: UPathSelector<State>) : this(selectors.toList())

    private var ptr = 0
    override fun isEmpty() = selectors[ptr].isEmpty() && selectors.all { it.isEmpty() }

    override fun peek(): State {
        val begin = ptr
        while (selectors[ptr].isEmpty()) {
            ptr = (ptr + 1) % selectors.size
            if (ptr == begin) {
                error("empty queue")
            }
        }
        return selectors[ptr].peek()
    }

    override fun update(state: State) {
        if (independent) {
            selectors[ptr].update(state)
        } else {
            selectors.forEach { it.update(state) }
        }
    }

    override fun add(states: Collection<State>) {
        if (independent) {
            selectors[ptr].add(states)
        } else {
            selectors.forEach { it.add(states) }
        }
        ptr = (ptr + 1) % selectors.size
    }

    override fun remove(state: State) {
        if (independent) {
            selectors[ptr].remove(state)
        } else {
            selectors.forEach { it.remove(state) }

        }
    }

}