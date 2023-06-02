package org.usvm.util

import java.util.*

class VanillaPriorityQueue<T, Priority>(comparator: Comparator<Priority>) : UPriorityCollection<T, Priority> {

    private val priorityQueue = PriorityQueue<Pair<T, Priority>> { (_, p1), (_, p2) -> comparator.compare(p1, p2) }

    override val count: Int get() = priorityQueue.size

    override fun peek(): T = priorityQueue.element().first

    override fun update(element: T, priority: Priority) {
        remove(element)
        priorityQueue.add(element to priority)
    }

    override fun remove(element: T) {
        if (!priorityQueue.removeIf { (e, _) -> e == element }) {
            throw NoSuchElementException("Element not found in priority queue")
        }
    }

    override fun add(element: T, priority: Priority) {
        check(!priorityQueue.any { (e, _) -> e == element }) { "Element already exists in priority queue" }
        priorityQueue.add(element to priority)
    }
}
