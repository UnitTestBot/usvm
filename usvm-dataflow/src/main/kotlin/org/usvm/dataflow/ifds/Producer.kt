/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ifds

import java.util.concurrent.atomic.AtomicReference

interface Producer<T> {
    fun produce(event: T)
    fun subscribe(consumer: Consumer<T>)
}

fun interface Consumer<in T> {
    fun consume(event: T)
}

/** Clearing the reference releases the captured callback even from an in-flight consumer snapshot. */
internal class CancellableConsumer<T>(consumer: Consumer<T>) : Consumer<T> {
    private val callback = AtomicReference<Consumer<T>?>(consumer)

    val isActive: Boolean
        get() = callback.get() != null

    fun cancel() {
        callback.set(null)
    }

    override fun consume(event: T) {
        callback.get()?.consume(event)
    }
}

private fun <T> Consumer<T>.isCancelled(): Boolean = this is CancellableConsumer<*> && !isActive

private fun <T> ConsList<Consumer<T>>.withoutCancelledConsumers(): ConsList<Consumer<T>> {
    val containsCancelledConsumer = any { it.isCancelled() }
    if (!containsCancelledConsumer) return this

    var result: ConsList<Consumer<T>> = Nil
    for (consumer in filterNot { it.isCancelled() }.asReversed()) {
        result = Cons(consumer, result)
    }
    return result
}

class SyncProducer<T> : Producer<T> {
    private val consumers: MutableList<Consumer<T>> = mutableListOf()
    private val events: MutableList<T> = mutableListOf()

    @Synchronized
    override fun produce(event: T) {
        for (consumer in consumers) {
            consumer.consume(event)
        }
        events.add(event)
    }

    @Synchronized
    override fun subscribe(consumer: Consumer<T>) {
        if (consumer.isCancelled()) return
        consumers.removeAll { it.isCancelled() }

        for (event in events) {
            consumer.consume(event)
        }
        if (!consumer.isCancelled()) consumers.add(consumer)
    }
}

sealed interface ConsList<out T> : Iterable<T>

data object Nil : ConsList<Nothing> {
    override fun iterator(): Iterator<Nothing> = object : Iterator<Nothing> {
        override fun hasNext(): Boolean = false
        override fun next(): Nothing = throw NoSuchElementException()
    }
}

data class Cons<out T>(
    val value: T,
    val tail: ConsList<T>,
) : ConsList<T> {
    override fun iterator(): Iterator<T> = Iter(this)

    private class Iter<T>(private var list: ConsList<T>) : Iterator<T> {
        override fun hasNext(): Boolean = list !is Nil

        override fun next(): T = when (val list = list) {
            is Nil -> throw NoSuchElementException()
            is Cons -> {
                val value = list.value
                this.list = list.tail
                value
            }
        }
    }
}

class NonBlockingQueue<T> {
    data class Node<T>(
        val value: T,
        @Volatile var next: Node<T>? = null,
    )

    var head: Node<T>? = null
        private set
    val tail: AtomicReference<Node<T>> = AtomicReference(head)

    fun add(element: T) {
        val node = Node(element)
        var currentTail: Node<T>?
        while (true) {
            currentTail = tail.get()
            if (tail.compareAndSet(currentTail, node)) break
        }
        if (currentTail != null) {
            currentTail.next = node
        } else {
            head = node
        }
    }
}

class ConcurrentProducer<T> : Producer<T> {
    private var consumers: AtomicReference<ConsList<Consumer<T>>> = AtomicReference(Nil)
    private val events: NonBlockingQueue<T> = NonBlockingQueue()

    override fun produce(event: T) {
        var currentConsumers: ConsList<Consumer<T>>
        while (true) {
            currentConsumers = consumers.get() ?: continue
            if (consumers.compareAndSet(currentConsumers, null)) break
        }

        events.add(event)

        // Publication and subscription stay atomic; callbacks remain synchronous
        // but may publish summaries or subscribe without holding this lock.
        check(consumers.compareAndSet(null, currentConsumers))

        for (consumer in currentConsumers) {
            consumer.consume(event)
        }
    }

    override fun subscribe(consumer: Consumer<T>) {
        if (consumer.isCancelled()) return

        var last: NonBlockingQueue.Node<T>? = null
        while (true) {
            val start = if (last != null) last.next else events.head
            var current = start
            while (current != null) {
                last = current
                consumer.consume(current.value)
                current = current.next
            }

            val currentConsumers = consumers.get() ?: continue
            if (!consumers.compareAndSet(currentConsumers, null)) continue
            if (events.tail.get() === last) {
                val retainedConsumers = currentConsumers.withoutCancelledConsumers()
                val newConsumers = if (consumer.isCancelled()) retainedConsumers else Cons(consumer, retainedConsumers)
                check(consumers.compareAndSet(null, newConsumers))
                break
            } else {
                check(consumers.compareAndSet(null, currentConsumers))
            }
        }
    }
}
