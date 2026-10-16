package org.usvm.algorithms

import org.usvm.util.assert
import java.util.PriorityQueue
import kotlin.Comparator
import kotlin.NoSuchElementException

/**
 * [UPriorityCollection] implementation based on [java.util.PriorityQueue].
 */
// TODO: what to do if elements have same priority?
class DeterministicPriorityCollection<T, Priority>(private val comparator: Comparator<Priority>) :
    UPriorityCollection<T, Priority> {
    private inner class Entry(val element: T, val priority: Priority) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int = comparator.compare(priority, other.priority)
    }

    private var topElement: Entry? = null
    private var priorityQueue = PriorityQueue<Entry>()

    override val count: Int get() = priorityQueue.size + if (topElement == null) 0 else 1

    override fun peek(): T {
        val currentTop = topElement
        if (currentTop != null) return currentTop.element

        val next = priorityQueue.remove()
        topElement = next
        return next.element
    }

    override fun update(element: T, priority: Priority) {
        remove(element)
        add(element, priority)
    }

    override fun remove(element: T) {
        // Don't traverse the whole queue if we remove the top element
        if (topElement != null && topElement?.element == element) {
            topElement = null
            return
        }

        if (!priorityQueue.removeIf { it.element == element }) {
            throw NoSuchElementException("Element not found in priority queue")
        }
    }

    override fun add(element: T, priority: Priority) {
        assert({ !priorityQueue.any { it.element == element } }) { "Element already exists in priority queue" }

        val currentTop = topElement
        if (currentTop != null && comparator.compare(currentTop.priority, priority) > 0) {
            topElement = Entry(element, priority)
            priorityQueue.add(currentTop)
            return
        }
        priorityQueue.add(Entry(element, priority))
    }

    /**
     * Recalculates each priority once and rebuilds the heap in linear time, without removing and reinserting elements.
     * If [priority] throws, the collection keeps its previous priorities and cached selection.
     */
    fun updatePriorities(priority: (T) -> Priority) {
        val entries = ArrayList<Entry>(count)
        val currentTop = topElement
        if (currentTop != null) {
            entries.add(Entry(currentTop.element, priority(currentTop.element)))
        }
        for (entry in priorityQueue) {
            entries.add(Entry(entry.element, priority(entry.element)))
        }

        // The collection constructor heapifies Comparable entries; addAll would perform individual insertions.
        val refreshedQueue = PriorityQueue(entries)
        priorityQueue = refreshedQueue
        topElement = null
    }
}
