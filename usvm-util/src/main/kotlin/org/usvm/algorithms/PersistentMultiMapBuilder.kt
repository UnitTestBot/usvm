package org.usvm.algorithms

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.toPersistentHashSet

typealias PersistentMultiMap<K, V> = PersistentMap<K, PersistentSet<V>>

/**
 * Provides an efficient way to perform multiple mutations on [PersistentMultiMap].
 * */
class PersistentMultiMapBuilder<K, V>(original: PersistentMultiMap<K, V>) : Iterable<Pair<K, V>> {
    private val map = original.builder()

    fun build(): PersistentMultiMap<K, V> = map.build()

    fun containsValue(key: K, value: V): Boolean =
        map[key]?.contains(value) ?: false

    fun isEmpty(): Boolean = map.isEmpty()

    operator fun get(key: K): Set<V>? = map[key]

    fun add(key: K, value: V) {
        val current = map[key]
        val updated = current?.add(value) ?: persistentHashSetOf(value)
        map[key] = updated
    }

    fun addAll(key: K, values: Set<V>) {
        val current = map[key]
        val updated = current?.addAll(values) ?: values.toPersistentHashSet()
        map[key] = updated
    }

    fun remove(key: K): Set<V>? = map.remove(key)

    fun removeValue(key: K, value: V) {
        val current = map[key] ?: return
        val updated = current.remove(value)
        map[key] = updated
    }

    fun removeAllValues(key: K, values: Set<V>) {
        val current = map[key] ?: return
        val updated = current.removeAll(values)
        map[key] = updated
    }

    fun clear() {
        map.clear()
    }

    override fun iterator(): Iterator<Pair<K, V>> = MultiMapIterator(map)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is PersistentMultiMapBuilder<*, *> -> map == other.map
        other is Map<*, *> -> map == other
        else -> false
    }

    override fun hashCode(): Int = map.hashCode()

    private class MultiMapIterator<K, V>(
        mapBuilder: MutableMap<K, PersistentSet<V>>
    ) : Iterator<Pair<K, V>> {
        private val valueIterators = mapBuilder.entries.mapTo(mutableListOf()) { it.key to it.value.iterator() }
        private var value: Pair<K, V>? = null

        override fun hasNext(): Boolean {
            propagate()
            return value != null
        }

        override fun next(): Pair<K, V> {
            propagate()
            val result = value ?: throw NoSuchElementException("Iterator is empty")
            value = null
            return result
        }

        private fun propagate() {
            while (value === null) {
                val lastIterator = valueIterators.lastOrNull() ?: return
                if (!lastIterator.second.hasNext()) {
                    valueIterators.removeLast()
                    continue
                }
                value = lastIterator.first to lastIterator.second.next()
            }
        }
    }
}
