package org.usvm.util

/**
 * Interface of a [T] elements collection prioritized with [Priority].
 */
interface UPriorityCollection<T, Priority> {

    /**
     * Number of elements in the collection.
     */
    val count: Int

    /**
     * Returns the element with the highest priority without
     * removing it.
     */
    fun peek(): T

    /**
     * Adds an element with specified priority.
     */
    fun add(element: T, priority: Priority)

    /**
     * Removes an element.
     */
    fun remove(element: T)

    /**
     * Updates the priority of an element.
     */
    fun update(element: T, priority: Priority)
}
