/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */


package org.usvm.collections.immutable

import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.implementations.immutableSet.UPersistentHashSet
import org.usvm.collections.immutable.internal.MutabilityOwnership


/**
 * Returns an empty persistent map.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> persistentHashMapOf(): UPersistentHashMap<K, V> = UPersistentHashMap.EMPTY as UPersistentHashMap<K, V>

fun <K, V> persistentHashMapOf(map: Map<K, V>, owner: MutabilityOwnership): UPersistentHashMap<K, V> =
    persistentHashMapOf<K, V>().putAll(map, owner)

fun <K, V> persistentHashMapOf(owner: MutabilityOwnership, vararg pairs: Pair<K, V>): UPersistentHashMap<K, V> =
    pairs.fold(persistentHashMapOf<K, V>()) { acc, (k, v) -> acc.put(k, v, owner) }

/**
 * Returns an empty persistent set.
 */
@Suppress("UNCHECKED_CAST")
public fun <E> persistentHashSetOf(): UPersistentHashSet<E> = UPersistentHashSet.EMPTY as UPersistentHashSet<E>
