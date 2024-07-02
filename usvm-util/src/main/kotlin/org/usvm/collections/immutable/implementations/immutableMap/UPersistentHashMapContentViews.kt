/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableMap

import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableSet
import org.usvm.collections.immutable.internal.MapImplementation

internal class UPersistentHashMapEntries<K, V>(private val map: UPersistentHashMap<K, V>) : ImmutableSet<Map.Entry<K, V>>, AbstractSet<Map.Entry<K, V>>() {
    override val size: Int get() = map.size

    override fun contains(element: Map.Entry<K, V>): Boolean {
        return MapImplementation.containsEntry(map, element)
    }

    override fun iterator(): Iterator<Map.Entry<K, V>> {
        return PersistentHashMapEntriesIterator(map.node)
    }
}

internal class PersistentHashMapKeys<K, V>(private val map: UPersistentHashMap<K, V>) : ImmutableSet<K>, AbstractSet<K>() {
    override val size: Int
        get() = map.size

    override fun contains(element: K): Boolean {
        return map.containsKey(element)
    }

    override fun iterator(): Iterator<K> {
        return PersistentHashMapKeysIterator(map.node)
    }
}

internal class PersistentHashMapValues<K, V>(private val map: UPersistentHashMap<K, V>) : ImmutableCollection<V>, AbstractCollection<V>() {
    override val size: Int
        get() = map.size

    override fun contains(element: V): Boolean {
        return map.containsValue(element)
    }

    override fun iterator(): Iterator<V> {
        return PersistentHashMapValuesIterator(map.node)
    }
}
