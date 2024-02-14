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
    private var topElement: Pair<T, Priority>? = null
    private val priorityQueue = PriorityQueue<Pair<T, Priority>> { (_, p1), (_, p2) -> comparator.compare(p1, p2) }

    override val count: Int get() = priorityQueue.size + if (topElement == null) 0 else 1

    override fun peek(): T {
        if (topElement == null) {
            topElement = priorityQueue.remove()
        }
        return topElement!!.first
    }

    override fun update(element: T, priority: Priority) {
        remove(element)
        add(element, priority)
    }

    override fun remove(element: T) {
        // Don't traverse the whole queue if we remove the top element
        if (topElement != null && topElement?.first == element) {
            topElement = null
            return
        }

        if (!priorityQueue.removeIf { (e, _) -> e == element }) {
            throw NoSuchElementException("Element not found in priority queue")
        }
    }

    override fun add(element: T, priority: Priority) {
        assert({ !priorityQueue.any { (e, _) -> e == element } }) { "Element already exists in priority queue" }

        val currentTop = topElement
        if (currentTop != null && comparator.compare(currentTop.second, priority) < 0) {
            topElement = element to priority
            priorityQueue.add(currentTop)
            return
        }
        priorityQueue.add(element to priority)
    }
}
