package org.usvm.util

class CachingSequence<T> private constructor(
    private val cache: MutableList<T>,
    private val sequence: Sequence<T>,
) : Sequence<T> {
    val iterator = sequence.iterator()

    constructor(iterator: Iterator<T>) : this(mutableListOf(), Sequence { iterator })
    constructor(sequence: Sequence<T>) : this(mutableListOf(), sequence)

    override fun iterator(): Iterator<T> = CachingIterator(0)

    private inner class CachingIterator(
        private var ptr: Int
    ) : Iterator<T> {
        override fun hasNext(): Boolean =
            ptr < cache.size || iterator.hasNext()

        override fun next(): T {
            if (ptr == cache.size) {
                cache += iterator.next()
            }
            return cache[ptr++]
        }
    }

    fun filter(filteringFunction: (T) -> Boolean): CachingSequence<T> {
        return CachingSequence(
            cache.filterTo(mutableListOf(), filteringFunction),
            CachingIterator(cache.size).asSequence().filter(filteringFunction),
        )
    }
}

fun <T> Sequence<T>.cached() = CachingSequence(this)