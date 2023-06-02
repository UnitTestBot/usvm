package org.usvm.util

interface UPriorityCollection<T, Priority> {
    val count: Int
    fun peek(): T
    fun add(element: T, priority: Priority)
    fun remove(element: T)
    fun update(element: T, priority: Priority)
}
