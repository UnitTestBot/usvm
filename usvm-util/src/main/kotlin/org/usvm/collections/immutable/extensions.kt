/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE")

package org.usvm.collections.immutable

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentCollection
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMapBuilder
import org.usvm.collections.immutable.implementations.persistentOrderedMap.PersistentOrderedMap
import org.usvm.collections.immutable.implementations.persistentOrderedMap.PersistentOrderedMapBuilder

//@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
//inline fun <T> @kotlin.internal.Exact ImmutableCollection<T>.mutate(mutator: (MutableCollection<T>) -> Unit): ImmutableCollection<T> = builder().apply(mutator).build()
// it or this?
/**
 * Returns the result of applying the provided modifications on this map.
 *
 * The mutable map passed to the [mutator] closure has the same contents as this persistent map.
 *
 * @return a new persistent map with the provided modifications applied;
 * or this instance if no modifications were made in the result of this operation.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <K, V> PersistentMap<out K, V>.mutate(mutator: (MutableMap<K, V>) -> Unit): PersistentMap<K, V> =
        (this as PersistentMap<K, V>).builder().apply(mutator).build()


/**
 * Returns the result of adding the specified [element] to this collection.
 *
 * @returns a new persistent collection with the specified [element] added;
 * or this instance if this collection does not support duplicates and it already contains the element.
 */
public inline operator fun <E> PersistentCollection<E>.plus(element: E): PersistentCollection<E> = add(element)

/**
 * Returns the result of removing a single appearance of the specified [element] from this collection.
 *
 * @return a new persistent collection with a single appearance of the specified [element] removed;
 * or this instance if there is no such element in this collection.
 */
public inline operator fun <E> PersistentCollection<E>.minus(element: E): PersistentCollection<E> = remove(element)


/**
 * Returns the result of adding all elements of the specified [elements] collection to this collection.
 *
 * @return a new persistent collection with elements of the specified [elements] collection added;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <E> PersistentCollection<E>.plus(elements: Iterable<E>): PersistentCollection<E>
        = if (elements is Collection) addAll(elements) else builder().also { it.addAll(elements) }.build()

/**
 * Returns the result of adding all elements of the specified [elements] array to this collection.
 *
 * @return a new persistent collection with elements of the specified [elements] array added;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <E> PersistentCollection<E>.plus(elements: Array<out E>): PersistentCollection<E>
        = builder().also { it.addAll(elements) }.build()

/**
 * Returns the result of adding all elements of the specified [elements] sequence to this collection.
 *
 * @return a new persistent collection with elements of the specified [elements] sequence added;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <E> PersistentCollection<E>.plus(elements: Sequence<E>): PersistentCollection<E>
        = builder().also { it.addAll(elements) }.build()


/**
 * Returns the result of removing all elements in this collection that are also
 * contained in the specified [elements] collection.
 *
 * @return a new persistent collection with elements in this collection that are also
 * contained in the specified [elements] collection removed;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <E> PersistentCollection<E>.minus(elements: Iterable<E>): PersistentCollection<E>
        = if (elements is Collection) removeAll(elements) else builder().also { it.removeAll(elements) }.build()

/**
 * Returns the result of removing all elements in this collection that are also
 * contained in the specified [elements] array.
 *
 * @return a new persistent collection with elements in this collection that are also
 * contained in the specified [elements] array removed;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <E> PersistentCollection<E>.minus(elements: Array<out E>): PersistentCollection<E>
        = builder().also { it.removeAll(elements) }.build()

/**
 * Returns the result of removing all elements in this collection that are also
 * contained in the specified [elements] sequence.
 *
 * @return a new persistent collection with elements in this collection that are also
 * contained in the specified [elements] sequence removed;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <E> PersistentCollection<E>.minus(elements: Sequence<E>): PersistentCollection<E>
        =  builder().also { it.removeAll(elements) }.build()

/**
 * Returns the result of adding an entry to this map from the specified key-value [pair].
 *
 * If this map already contains a mapping for the key,
 * the old value is replaced by the value from the specified [pair].
 *
 * @return a new persistent map with an entry from the specified key-value [pair] added;
 * or this instance if no modifications were made in the result of this operation.
 */
@Suppress("UNCHECKED_CAST")
public inline operator fun <K, V> PersistentMap<out K, V>.plus(pair: Pair<K, V>): PersistentMap<K, V>
        = (this as PersistentMap<K, V>).put(pair.first, pair.second)

/**
 * Returns the result of replacing or adding entries to this map from the specified key-value pairs.
 *
 * @return a new persistent map with entries from the specified key-value pairs added;
 * or this instance if no modifications were made in the result of this operation.
 */
public inline operator fun <K, V> PersistentMap<out K, V>.plus(pairs: Iterable<Pair<K, V>>): PersistentMap<K, V> = putAll(pairs)

/**
 * Returns the result of replacing or adding entries to this map from the specified key-value pairs.
 *
 * @return a new persistent map with entries from the specified key-value pairs added;
 * or this instance if no modifications were made in the result of this operation.
 */
public inline operator fun <K, V> PersistentMap<out K, V>.plus(pairs: Array<out Pair<K, V>>): PersistentMap<K, V> = putAll(pairs)

/**
 * Returns the result of replacing or adding entries to this map from the specified key-value pairs.
 *
 * @return a new persistent map with entries from the specified key-value pairs added;
 * or this instance if no modifications were made in the result of this operation.
 */
public inline operator fun <K, V> PersistentMap<out K, V>.plus(pairs: Sequence<Pair<K, V>>): PersistentMap<K, V> = putAll(pairs)

/**
 * Returns the result of merging the specified [map] with this map.
 *
 * The effect of this call is equivalent to that of calling `put(k, v)` once for each
 * mapping from key `k` to value `v` in the specified map.
 *
 * @return a new persistent map with keys and values from the specified [map] associated;
 * or this instance if no modifications were made in the result of this operation.
 */
public inline operator fun <K, V> PersistentMap<out K, V>.plus(map: Map<out K, V>): PersistentMap<K, V> = putAll(map)


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
public fun <K, V> PersistentMap<out K, V>.putAll(map: Map<out K, V>): PersistentMap<K, V> =
        (this as PersistentMap<K, V>).putAll(map)

/**
 * Returns the result of replacing or adding entries to this map from the specified key-value pairs.
 *
 * @return a new persistent map with entries from the specified key-value pairs added;
 * or this instance if no modifications were made in the result of this operation.
 */
public fun <K, V> PersistentMap<out K, V>.putAll(pairs: Iterable<Pair<K, V>>): PersistentMap<K, V>
        = mutate { it.putAll(pairs) }

/**
 * Returns the result of replacing or adding entries to this map from the specified key-value pairs.
 *
 * @return a new persistent map with entries from the specified key-value pairs added;
 * or this instance if no modifications were made in the result of this operation.
 */
public fun <K, V> PersistentMap<out K, V>.putAll(pairs: Array<out Pair<K, V>>): PersistentMap<K, V>
        = mutate { it.putAll(pairs) }

/**
 * Returns the result of replacing or adding entries to this map from the specified key-value pairs.
 *
 * @return a new persistent map with entries from the specified key-value pairs added;
 * or this instance if no modifications were made in the result of this operation.
 */
public fun <K, V> PersistentMap<out K, V>.putAll(pairs: Sequence<Pair<K, V>>): PersistentMap<K, V>
        = mutate { it.putAll(pairs) }


/**
 * Returns the result of removing the specified [key] and its corresponding value from this map.
 *
 * @return a new persistent map with the specified [key] and its corresponding value removed;
 * or this instance if it contains no mapping for the key.
 */
@Suppress("UNCHECKED_CAST")
public operator fun <K, V> PersistentMap<out K, V>.minus(key: K): PersistentMap<K, V>
        = (this as PersistentMap<K, V>).remove(key)

/**
 * Returns the result of removing the specified [keys] and their corresponding values from this map.
 *
 * @return a new persistent map with the specified [keys] and their corresponding values removed;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <K, V> PersistentMap<out K, V>.minus(keys: Iterable<K>): PersistentMap<K, V>
        = mutate { it.minusAssign(keys) }

/**
 * Returns the result of removing the specified [keys] and their corresponding values from this map.
 *
 * @return a new persistent map with the specified [keys] and their corresponding values removed;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <K, V> PersistentMap<out K, V>.minus(keys: Array<out K>): PersistentMap<K, V>
        = mutate { it.minusAssign(keys) }

/**
 * Returns the result of removing the specified [keys] and their corresponding values from this map.
 *
 * @return a new persistent map with the specified [keys] and their corresponding values removed;
 * or this instance if no modifications were made in the result of this operation.
 */
public operator fun <K, V> PersistentMap<out K, V>.minus(keys: Sequence<K>): PersistentMap<K, V>
        = mutate { it.minusAssign(keys) }

/**
 * Returns a new persistent map with the specified contents, given as a list of pairs
 * where the first component is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 *
 * Entries of the map are iterated in the order they were specified.
 */
public fun <K, V> persistentMapOf(vararg pairs: Pair<K, V>): PersistentMap<K, V> = PersistentOrderedMap.emptyOf<K,V>().mutate { it += pairs }

/**
 * Returns an empty persistent map.
 */
public fun <K, V> persistentMapOf(): PersistentMap<K, V> = PersistentOrderedMap.emptyOf()


/**
 * Returns a new persistent map with the specified contents, given as a list of pairs
 * where the first component is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 *
 * Order of the entries in the returned map is unspecified.
 */
public fun <K, V> persistentHashMapOf(vararg pairs: Pair<K, V>): PersistentMap<K, V> = UPersistentHashMap.emptyOf<K,V>().mutate { it += pairs }

/**
 * Returns an empty persistent map.
 */
public fun <K, V> persistentHashMapOf(): PersistentMap<K, V> = UPersistentHashMap.emptyOf()

/**
 * Returns a new persistent map with the specified contents, given as a list of pairs
 * where the first component is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 *
 * Entries of the map are iterated in the order they were specified.
 */
@Deprecated("Use persistentMapOf instead.", ReplaceWith("persistentMapOf(*pairs)"))
public fun <K, V> immutableMapOf(vararg pairs: Pair<K, V>): PersistentMap<K, V> = persistentMapOf(*pairs)

/**
 * Returns a new persistent map with the specified contents, given as a list of pairs
 * where the first component is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 *
 * Order of the entries in the returned map is unspecified.
 */
@Deprecated("Use persistentHashMapOf instead.", ReplaceWith("persistentHashMapOf(*pairs)"))
public fun <K, V> immutableHashMapOf(vararg pairs: Pair<K, V>): PersistentMap<K, V> = persistentHashMapOf(*pairs)

/**
 * Returns an immutable map containing all entries from this map.
 *
 * If the receiver is already an immutable map, returns it as is.
 *
 * Entries of the returned map are iterated in the same order as in this map.
 */
public fun <K, V> Map<K, V>.toImmutableMap(): ImmutableMap<K, V>
    = this as? ImmutableMap
        ?: (this as? PersistentMap.Builder)?.build()
        ?: persistentMapOf<K, V>().putAll(this)

/**
 * Returns a persistent map containing all entries from this map.
 *
 * If the receiver is already a persistent map, returns it as is.
 * If the receiver is a persistent map builder, calls `build` on it and returns the result.
 *
 * Entries of the returned map are iterated in the same order as in this map.
 */
public fun <K, V> Map<K, V>.toPersistentMap(): PersistentMap<K, V>
    = this as? PersistentOrderedMap<K, V>
        ?: (this as? PersistentOrderedMapBuilder<K, V>)?.build()
        ?: PersistentOrderedMap.emptyOf<K, V>().putAll(this)

/**
 * Returns an immutable map containing all entries from this map.
 *
 * If the receiver is already a persistent hash map, returns it as is.
 * If the receiver is a persistent hash map builder, calls `build` on it and returns the result.
 *
 * Order of the entries in the returned map is unspecified.
 */
public fun <K, V> Map<K, V>.toPersistentHashMap(): PersistentMap<K, V>
        = this as? UPersistentHashMap
        ?: (this as? UPersistentHashMapBuilder<K, V>)?.build()
        ?: UPersistentHashMap.emptyOf<K, V>().putAll(this)
