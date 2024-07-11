/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE")

package org.usvm.collections.immutable

import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.implementations.immutableSet.UPersistentHashSet
import org.usvm.collections.immutable.internal.MutabilityOwnership

/**
 * Returns the result of merging the specified [map] with this map.
 *
 * The effect of this call is equivalent to that of calling `put(k, v)` once for each
 * mapping from key `k` to value `v` in the specified map.
 *
 * @return a new persistent map with keys and values from the specified [map] associated;
 * or this instance if no modifications were made in the result of this operation.
 */
public inline operator fun <K, V> UPersistentHashMap<out K, V>.plus(map: Map<out K, V>): UPersistentHashMap<K, V> = putAll(map)


/**
 * Returns the result of merging the specified [map] with this map.
 *
 * The effect of this call is equivalent to that of calling `put(k, v)` once for each
 * mapping from key `k` to value `v` in the specified map.
 *
 * @return a new persistent map with keys and values from the specified [map] associated;
 * or this instance if no modifications were made in the result of this operation.
 */
@Suppress("UNCHECKED_CAST")
public fun <K, V> UPersistentHashMap<out K, V>.putAll(map: Map<out K, V>): UPersistentHashMap<K, V> =
        (this as UPersistentHashMap<K, V>).putAll(map)

/**
 * Returns a new persistent map with the specified contents, given as a list of pairs
 * where the first component is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 *
 * Entries of the map are iterated in the order they were specified.
 */
//public fun <K, V> persistentMapOf(ownership: MutabilityOwnership, vararg pairs: Pair<K, V>): UPersistentHashMap<K, V> = 
//        persistentMapBuilderOf(ownership, *pairs).build()

/**
 * Returns an empty persistent map.
 */
// public fun <K, V> persistentMapOf(): UPersistentHashMap<K, V> = UPersistentOrderedMap.emptyOf()

/**
 * Returns an empty persistent map.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> persistentHashMapOf(): UPersistentHashMap<K, V> = UPersistentHashMap.EMPTY as UPersistentHashMap<K, V>

fun <K, V> persistentHashMapOf(map : Map<K,V>, owner : MutabilityOwnership): UPersistentHashMap<K, V> =
        persistentHashMapOf<K, V>().putAll(map, owner)


/**
 * Returns a persistent map containing all entries from this map.
 *
 * If the receiver is already a persistent map, returns it as is.
 * If the receiver is a persistent map builder, calls `build` on it and returns the result.
 *
 * Entries of the returned map are iterated in the same order as in this map.
 */
//fun <K, V> Map<K, V>.toPersistentMap(): UPersistentHashMap<K, V>
//    = this as? UPersistentOrderedMap<K, V>
//        ?: (this as? UPersistentOrderedMapBuilder<K, V>)?.build()
//        ?: UPersistentOrderedMap.emptyOf<K, V>().putAll(this)

/**
 * Returns an immutable map containing all entries from this map.
 *
 * If the receiver is already a persistent hash map, returns it as is.
 * If the receiver is a persistent hash map builder, calls `build` on it and returns the result.
 *
 * Order of the entries in the returned map is unspecified.
 */
fun <K, V> Map<K, V>.toPersistentHashMap(ownership: MutabilityOwnership): UPersistentHashMap<K, V> =
        persistentHashMapOf(this, ownership)

/**
 * Returns an empty persistent set.
 */
@Suppress("UNCHECKED_CAST")
public fun <E> persistentHashSetOf(): UPersistentHashSet<E> = UPersistentHashSet.EMPTY as UPersistentHashSet<E>

/**
 * Returns a new persistent set with the given elements.
 *
 * Order of the elements in the returned set is unspecified.
 */
public fun <E> persistentHashSetOf(elements : Collection<E>, ownership: MutabilityOwnership): UPersistentHashSet<E> =
        persistentHashSetOf<E>().addAll(elements, ownership) 

