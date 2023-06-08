package org.usvm.util

abstract class GraphIterator<T> : Iterator<T> {
    protected sealed class IteratorState {
        object Unknown : IteratorState()
        object NoElements : IteratorState()
        class Element<T>(val element: T) : IteratorState()
    }

    private var iteratorState: IteratorState = IteratorState.Unknown

    protected abstract fun calcNext(): IteratorState

    final override fun hasNext(): Boolean {
        if (iteratorState == IteratorState.Unknown) {
            calcNext()
        }
        return iteratorState is IteratorState.Element<*>
    }

    final override fun next(): T {
        if (iteratorState == IteratorState.Unknown) {
            calcNext()
        }
        if (iteratorState == IteratorState.NoElements) {
            throw NoSuchElementException()
        }
        @Suppress("UNCHECKED_CAST")
        val result = (iteratorState as IteratorState.Element<T>).element
        iteratorState = IteratorState.Unknown
        return result
    }
}

class DfsIterator<T>(
    top: T,
    private val itemToChildren: (T) -> Iterator<T>,
) : GraphIterator<T>() {
    private val queue = ArrayDeque<Node<T>>().apply { add(Node(top, itemToChildren(top))) }
    private var used = hashSetOf<T>()
    private data class Node<T>(val item: T, val children: Iterator<T>)
    override fun calcNext(): IteratorState {
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
                return IteratorState.Element(item)
            }
        }
        return IteratorState.NoElements
    }
}