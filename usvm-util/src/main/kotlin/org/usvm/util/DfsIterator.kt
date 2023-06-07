package org.usvm.util

class DfsIterator<T>(
    top: T,
    private val itemToChildren: (T) -> Iterator<T>,
) : Iterator<T> {
    private val queue = mutableListOf(Node(top, itemToChildren(top)))
    private var used = hashSetOf<T>()

    private data class Node<T>(val item: T, val children: Iterator<T>)
    private enum class NextState {
        UNKNOWN,
        NO_ELEMENTS,
        ELEMENT_AVAILABLE,
    }

    private var nextState =
        NextState.UNKNOWN // -1 for unknown, 0 for no elements left, 1 for next element is available.
    private var nextItem: T? = null

    private fun calcNext() {
        while (queue.isNotEmpty()) {
            val (item, children) = queue.last()

            var nextItem: T? = null
            while (nextItem == null && children.hasNext()) {
                nextItem = children.next().takeIf { it !in used }
            }
            if (nextItem != null) {
                used += nextItem
                val nextChildren = itemToChildren(nextItem)
                queue.add(Node(nextItem, nextChildren))
            } else { // nextItem == null && children.hasNext() == false
                queue.removeLast()
                this.nextState = NextState.ELEMENT_AVAILABLE
                this.nextItem = item
                return
            }
        }
        nextState = NextState.NO_ELEMENTS
    }

    override fun hasNext(): Boolean {
        if (nextState == NextState.UNKNOWN) {
            calcNext()
        }
        return nextState == NextState.ELEMENT_AVAILABLE
    }

    override fun next(): T {
        if (nextState == NextState.UNKNOWN) {
            calcNext()
        }
        if (nextState == NextState.NO_ELEMENTS) {
            throw NoSuchElementException()
        }
        val result = nextItem
        nextItem = null
        nextState = NextState.UNKNOWN
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}