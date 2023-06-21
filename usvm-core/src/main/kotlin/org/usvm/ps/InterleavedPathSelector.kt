package org.usvm.ps

import org.usvm.UPathSelector

class InterleavedPathSelector<State>(
    private val selectors: List<UPathSelector<State>>
) : UPathSelector<State> {

    constructor(vararg selectors: UPathSelector<State>) : this(selectors.toList())

    init {
        require(selectors.isNotEmpty()) { "Empty path selectors list is passed to InterleavedPathSelector" }
    }

    private var ptr = -1

    override fun isEmpty() = selectors[ptr].isEmpty()

    override fun peek(): State {
        ptr = (ptr + 1) % selectors.size
        return selectors[ptr].peek()
    }

    override fun update(state: State) {
        selectors.forEach { it.update(state) }
    }

    override fun add(states: Collection<State>) {
        selectors.forEach { it.add(states) }
    }

    override fun remove(state: State) {
        selectors.forEach { it.remove(state) }
    }
}
