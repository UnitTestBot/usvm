/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */


package org.usvm.collections.immutable

import org.usvm.collections.immutable.implementations.immutableMap.TrieNode
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.implementations.immutableSet.UPersistentHashSet
import org.usvm.collections.immutable.internal.MutabilityOwnership


fun <E> UPersistentHashSet<E>.isEmpty() = none()

fun <E> UPersistentHashSet<E>.isNotEmpty() = any()

fun <E> UPersistentHashSet<E>.addAll(elements: Collection<E>, owner: MutabilityOwnership) : UPersistentHashSet<E> =
    elements.fold(this) { node, e -> node.add(e, owner) }

fun <E> UPersistentHashSet<E>.removeAll(elements: Collection<E>, owner: MutabilityOwnership): UPersistentHashSet<E> =
    elements.fold(this) { node, e -> node.remove(e, owner) }

fun <E> UPersistentHashSet<E>.removeAll(elements: Iterable<E>, owner: MutabilityOwnership): UPersistentHashSet<E> =
    elements.fold(this) { node, e -> node.remove(e, owner) }

fun <E> UPersistentHashSet<E>.containsAll(elements: Collection<E>): Boolean = elements.all { e -> this.contains(e) }

fun <K, V> UPersistentHashMap<K, V>.isEmpty() = none()

fun <K, V> UPersistentHashMap<K, V>.isNotEmpty() = any()

fun <K, V> UPersistentHashMap<K, V>.getOrDefault(key: K, defaultValue: V) = get(key) ?: defaultValue

inline fun <K, V> UPersistentHashMap<K, V>.getOrPut(
    key: K,
    owner: MutabilityOwnership,
    defaultValue: () -> V,
): Pair<TrieNode<K, V>, V> {
    val current = get(key) ?: defaultValue().let { return put(key, it, owner) to it }
    return this to current
}

fun <K, V> UPersistentHashMap<K, V>.removeAll(keys: Iterable<K>, owner: MutabilityOwnership): UPersistentHashMap<K, V> =
    keys.fold(this) { node, k -> node.remove(k, owner) }

fun <K, V> UPersistentHashMap<K, V>.putAll(map: Map<K, V>, owner: MutabilityOwnership): TrieNode<K, V> =
    map.asSequence().fold(this) { acc, entry -> acc.put(entry.key, entry.value, owner) }

fun <K, V> UPersistentHashMap<K, V>.toMutableMap(): MutableMap<K, V> =
    mutableMapOf<K, V>().also { this.forEach { entry -> it[entry.key] = entry.value } }

/**
 * Returns an empty persistent map.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> persistentHashMapOf(): UPersistentHashMap<K, V> = UPersistentHashMap.EMPTY as UPersistentHashMap<K, V>

fun <K, V> persistentHashMapOf(map: Map<K, V>, owner: MutabilityOwnership): UPersistentHashMap<K, V> =
    persistentHashMapOf<K, V>().putAll(map, owner)

fun <K, V> persistentHashMapOf(owner: MutabilityOwnership, vararg pairs: Pair<K, V>): UPersistentHashMap<K, V> =
    pairs.fold(persistentHashMapOf()) { acc, (k, v) -> acc.put(k, v, owner) }

/**
 * Returns an empty persistent set.
 */
@Suppress("UNCHECKED_CAST")
public fun <E> persistentHashSetOf(): UPersistentHashSet<E> = UPersistentHashSet.EMPTY as UPersistentHashSet<E>
