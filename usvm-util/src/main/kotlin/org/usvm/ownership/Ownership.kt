package org.usvm.ownership

import kotlinx.collections.immutable.PersistentMap

class Ownership

interface Ownerable<T : Ownerable<T>> {
    val ownership: Ownership
    fun clone(ownership: Ownership): T
}

class State(
    private var memory: PersistentMap<Int, MemoryRegion>,
) {
    private var ownership = Ownership()
    fun readKey(key1: Int, key2: Int, key3: Int): Int =
        memory.getValue(key1).readKey(key2, key3)

    fun writeKey(key1: Int, key2: Int, key3: Int, value: Int) {
        val region = memory.getValue(key1)
        val newRegion = with(ownership) { region.writeKey(key2, key3, value) }
        memory = memory.put(key1, newRegion)
    }

    fun clone(): State {
        ownership = Ownership()
        return State(memory)
    }
}

class MemoryRegion(
    override val ownership: Ownership,
    private var collections: TrieNode<Int, Collection> // TODO: extract trie node
) : Ownerable<MemoryRegion> {
    fun readKey(key2: Int, key3: Int): Int =
        collections.getValue(key2).readKey(key3)

    context(Ownership)
    fun writeKey(key2: Int, key3: Int, value: Int) = mutableUpdate {
        val oldCollection = collections.getValue(key2)
        val newCollection = oldCollection.writeKey(key3, value)
        collections = collections.mutablePut(key2, newCollection, ownership)
    }

    override fun clone(ownership: Ownership) = MemoryRegion(ownership, collections)
}

class Collection(
    override val ownership: Ownership,
    private var values: PersistentMap<Int, Int>
) : Ownerable<Collection> {
    fun readKey(key3: Int): Int =
        values.getValue(key3)

    context(Ownership)
    fun writeKey(key3: Int, value: Int): Collection = mutableUpdate {
        val newValue = values.put(key3, value)
        values = newValue
    }

    override fun clone(ownership: Ownership): Collection = Collection(ownership, values)
}

context(Ownership)
inline fun <T : Ownerable<T>> T.mutableUpdate(write: T.() -> Unit): T =
    if (ownership == this@Ownership) {
        apply(write)
    } else {
        clone(this@Ownership).apply(write)
    }
