package org.usvm.algorithms

import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.implementations.immutableSet.UPersistentHashSet
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashSetOf

typealias UPersistentMultiMap<K,V> = UPersistentHashMap<K, UPersistentHashSet<V>>

//typealias UPersistentMultiMap<K, V> = UPersistentHashMap<K, UPersistentHashSet<V>>

/**
 * Provides an efficient way to perform multiple mutations on [PersistentMultiMap].
 * */

fun <K,V> UPersistentMultiMap<K,V>.containsValue(key : K, value : V) : Boolean =
    this[key]?.contains(value) ?: false

fun <K,V> UPersistentMultiMap<K,V>.addToSet(
    key : K,
    value: V,
    ownership: MutabilityOwnership,
): UPersistentMultiMap<K,V> {
    val current = this[key]
    val updated = current?.add(value, ownership) ?: persistentHashSetOf<V>().add(value, ownership)
    return this.put(key, updated, ownership)
}

fun <K,V> UPersistentMultiMap<K,V>.addAll(
    key : K,
    values: Set<V>,
    ownership: MutabilityOwnership,
) : UPersistentMultiMap<K,V> {
    val current = this[key]
    val updated = current?.addAll(values, ownership) ?: persistentHashSetOf<V>().addAll(values, ownership)
    return this.put(key, updated, ownership)
}

fun <K,V> UPersistentMultiMap<K,V>.removeValue(
    key : K,
    value: V,
    ownership: MutabilityOwnership
) : UPersistentMultiMap<K,V> {
    val current = this[key] ?: return this
    val updated = current.remove(value, ownership)
    if (updated.isEmpty()) return this.remove(key, ownership)
    return this.put(key, updated, ownership)
}

fun <K,V> UPersistentMultiMap<K,V>.removeAllValues(
    key : K,
    values: Iterable<V>,
    ownership: MutabilityOwnership
) : UPersistentMultiMap<K,V> {
    val current = this[key] ?: return this
    val updated = current.removeAll(values, ownership)
    return this.put(key, updated, ownership)
}

fun <K,V> UPersistentMultiMap<K,V>.multiMapIterator() = MultiMapIterator(this)

class MultiMapIterator<K, V>(multiMap: UPersistentMultiMap<K, V>) : Iterator<Pair<K, V>> {
    private val valueIterators = multiMap.mapTo(mutableListOf()) { it.key to it.value.iterator() }
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

