package org.usvm.util

/**
 * A caching sequence for an [iterator]. Can be iterated multiple times, since every value obtained from the iterator
 * is cached.
 *
 * The cache is **NOT** thread-safe.
 */
class CachingSequence<T> private constructor(
    private val cache: MutableList<T>,
    private val iterator: Iterator<T>,
) : Sequence<T> {
    /**
     * Creates a caching sequence from an iterator.
     */
    constructor(iterator: Iterator<T>) : this(mutableListOf(), iterator)

    /**
     * Creates a caching sequence from a [sequence], preserving property that the original [sequence]
     * will be iterated only once.
     */
    constructor(sequence: Sequence<T>) : this(mutableListOf(), sequence.iterator())

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
            CachingIterator(cache.size).asSequence().filter(filteringFunction).iterator(),
        )
    }
}

/**
 * Wraps [this] into a caching sequence.
 *
 * @see CachingSequence
 */
fun <T> Sequence<T>.cached() = CachingSequence(this)