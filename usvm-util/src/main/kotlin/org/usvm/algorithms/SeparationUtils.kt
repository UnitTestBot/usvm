package org.usvm.algorithms

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

data class SeparationResult<C>(
    val overlap: C,
    val leftUnique: C,
    val rightUnique: C,
)

fun <E> PersistentSet<E>.separate(other: PersistentSet<E>): SeparationResult<PersistentSet<E>> {
    val overlap = this.retainAll(other)
    val leftUnique = this.removeAll(overlap)
    val rightUnique = other.removeAll(overlap)
    return SeparationResult(overlap, leftUnique, rightUnique)
}

fun <K, V> PersistentMap<K, V>.separate(other: PersistentMap<K, V>): SeparationResult<PersistentMap<K, V>> {
    val overlap = this.builder().apply { entries.retainAll(other.entries) }.build()
    val leftUnique = this.builder().apply { keys.removeAll(overlap.keys) }.build()
    val rightUnique = other.builder().apply { keys.removeAll(overlap.keys) }.build()
    return SeparationResult(overlap, leftUnique, rightUnique)
}