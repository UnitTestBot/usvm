/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableMap

import org.usvm.collections.immutable.internal.MutabilityOwnership


internal class TrieNodeMutableEntriesIterator<K, V>(
    private val parentIterator: TrieMutableIterator<K, V>
) : TrieNodeBaseIterator<K, V, MutableMap.MutableEntry<K, V>>() {

    override fun next(): MutableMap.MutableEntry<K, V> {
        assert(hasNextKey())
        index += 2
        @Suppress("UNCHECKED_CAST")
        return MutableMapEntry(parentIterator, buffer[index - 2] as K, buffer[index - 1] as V)
    }
}

private class MutableMapEntry<K, V>(
    private val parentIterator: TrieMutableIterator<K, V>,
    key: K,
    override var value: V,
) : MapEntry<K, V>(key, value), MutableMap.MutableEntry<K, V> {

    override fun setValue(newValue: V): V {
        val result = value
        value = newValue
        parentIterator.setValue(key, newValue)
        return result
    }
}


internal open class UPersistentHashSetMutableIterator<K, V, T>(
    private val node: TrieNode<K, V>,
    path: Array<TrieNodeBaseIterator<K, V, T>>,
    val ownership: MutabilityOwnership,
) : MutableIterator<T>, UPersistentHashMapBaseIterator<K, V, T>(node, path) {

    private var lastIteratedKey: K? = null
    private var nextWasInvoked = false

    override fun next(): T {
        lastIteratedKey = currentKey()
        nextWasInvoked = true
        return super.next()
    }

    override fun remove() {
        checkNextWasInvoked()
        if (hasNext()) {
            val currentKey = currentKey()

            node.remove(lastIteratedKey!!, ownership)
            resetPath(currentKey.hashCode(), node, currentKey, 0)
        } else {
            node.remove(lastIteratedKey!!, ownership)
        }

        lastIteratedKey = null
        nextWasInvoked = false
    }

    fun setValue(key: K, newValue: V) {
        if (!node.containsKey(key)) return

        if (hasNext()) {
            val currentKey = currentKey()

            node.put(key, newValue, ownership)
            resetPath(currentKey.hashCode(), node, currentKey, 0)
        } else {
            node.put(key, newValue, ownership)
        }
    }

    private fun resetPath(keyHash: Int, node: TrieNode<*, *>, key: K, pathIndex: Int) {
        val shift = pathIndex * LOG_MAX_BRANCHING_FACTOR

        if (shift > MAX_SHIFT) {    // collision
            path[pathIndex].reset(node.buffer, node.buffer.size, 0)
            while (path[pathIndex].currentKey() != key) {
                path[pathIndex].moveToNextKey()
            }
            pathLastIndex = pathIndex
            return
        }

        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (node.hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = node.entryKeyIndex(keyPositionMask)

//            assert(node.keyAtIndex(keyIndex) == key)

            path[pathIndex].reset(node.buffer, ENTRY_SIZE * node.entryCount(), keyIndex)
            pathLastIndex = pathIndex
            return
        }

//        assert(node.hasNodeAt(keyPositionMask)) // key is in node

        val nodeIndex = node.nodeIndex(keyPositionMask)
        val targetNode = node.nodeAtIndex(nodeIndex)
        path[pathIndex].reset(node.buffer, ENTRY_SIZE * node.entryCount(), nodeIndex)
        resetPath(keyHash, targetNode, key, pathIndex + 1)
    }

    private fun checkNextWasInvoked() {
        if (!nextWasInvoked)
            throw IllegalStateException()
    }
}

internal class TrieMutableIterator<K, V>(
    node: TrieNode<K, V>,
    ownership: MutabilityOwnership,
) : MutableIterator<MutableMap.MutableEntry<K, V>> {
    private val base = UPersistentHashSetMutableIterator<K, V, MutableMap.MutableEntry<K, V>>(
        node,
        Array(TRIE_MAX_HEIGHT + 1) { TrieNodeMutableEntriesIterator(this) },
        ownership
    )

    override fun hasNext(): Boolean = base.hasNext()
    override fun next(): MutableMap.MutableEntry<K, V> = base.next()
    override fun remove(): Unit = base.remove()

    fun setValue(key: K, newValue: V): Unit = base.setValue(key, newValue)
}
