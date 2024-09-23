package org.usvm.algorithms

import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.implementations.immutableSet.UPersistentHashSet
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf

data class SeparationResult<C>(
    val overlap: C,
    val leftUnique: C,
    val rightUnique: C,
)

fun <E> UPersistentHashSet<E>.separate(
    other: UPersistentHashSet<E>,
    ownership: MutabilityOwnership
): SeparationResult<UPersistentHashSet<E>> {
    val overlap = this.retainAll(other, ownership)
    val leftUnique = this.removeAll(overlap, ownership)
    val rightUnique = other.removeAll(overlap, ownership)
    return SeparationResult(overlap, leftUnique, rightUnique)
}

// should be used with ownership that does not occur in both maps so that they will not be mutated
fun <K, V> UPersistentHashMap<K, V>.separate(
    other: UPersistentHashMap<K, V>,
    ownership: MutabilityOwnership,
): SeparationResult<UPersistentHashMap<K, V>> {
    val overlap =
        this.fold(persistentHashMapOf<K, V>()) { map, entry ->
            if (other.containsKey(entry.key)) map.put(entry.key, entry.value, ownership) else map
        }
    val leftUnique = overlap.fold(this) { map, entry -> map.remove(entry.key, ownership) }
    val rightUnique = overlap.fold(other) { map, entry -> map.remove(entry.key, ownership) }
    return SeparationResult(overlap, leftUnique, rightUnique)
}
