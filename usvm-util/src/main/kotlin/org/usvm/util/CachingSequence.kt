package org.usvm.util

class CachingSequence<T> private constructor(
    private val cache: MutableList<T>,
    private val sequence: Sequence<T>,
) : Sequence<T> {
    val iterator = sequence.iterator()

    constructor(iterator: Iterator<T>) : this(mutableListOf(), Sequence { iterator })
    constructor(sequence: Sequence<T>) : this(mutableListOf(), sequence)

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var ptr = 0
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
            sequence.filter(filteringFunction),
        )
    }
}


