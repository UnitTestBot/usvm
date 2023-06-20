package org.usvm.ps.combinators

import org.usvm.UPathSelector

/**
 * A parallel path selector.
 *
 * [update], [remove] and [add] operations are executed only on the current path selector. A pointer to
 * the next path selector advances when [add] is called.
 */
class ParallelSelector<State>(
    val selectors: List<UPathSelector<State>>,
) : UPathSelector<State> {
    constructor(vararg selectors: UPathSelector<State>) : this(selectors.toList())

    private var ptr = 0
    override fun isEmpty() = selectors[ptr].isEmpty() && selectors.all { it.isEmpty() }

    override fun peek(): State {
        val begin = ptr
        while (selectors[ptr].isEmpty()) {
            ptr = (ptr + 1) % selectors.size
            if (ptr == begin) {
                error("Empty queue")
            }
        }
        return selectors[ptr].peek()
    }

    override fun update(state: State) {
        selectors[ptr].update(state)
    }

    override fun add(states: Collection<State>) {
        selectors[ptr].add(states)
        ptr = (ptr + 1) % selectors.size
    }

    override fun remove(state: State) {
        selectors[ptr].remove(state)
    }
}