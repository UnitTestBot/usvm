/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableMap

import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.internal.forEachOneBit

typealias UPersistentHashMap<K, V> = TrieNode<K, V>


internal const val MAX_BRANCHING_FACTOR = 32
internal const val LOG_MAX_BRANCHING_FACTOR = 5
internal const val MAX_BRANCHING_FACTOR_MINUS_ONE = MAX_BRANCHING_FACTOR - 1
internal const val ENTRY_SIZE = 2
internal const val MAX_SHIFT = 30

/**
 * Gets trie index segment of the specified [index] at the level specified by [shift].
 *
 * `shift` equal to zero corresponds to the root level.
 * For each lower level `shift` increments by [LOG_MAX_BRANCHING_FACTOR].
 */
internal fun indexSegment(index: Int, shift: Int): Int =
    (index shr shift) and MAX_BRANCHING_FACTOR_MINUS_ONE

private fun <K, V> Array<Any?>.insertEntryAtIndex(keyIndex: Int, key: K, value: V): Array<Any?> {
    val newBuffer = arrayOfNulls<Any?>(this.size + ENTRY_SIZE)
    this.copyInto(newBuffer, endIndex = keyIndex)
    this.copyInto(newBuffer, keyIndex + ENTRY_SIZE, startIndex = keyIndex, endIndex = this.size)
    newBuffer[keyIndex] = key
    newBuffer[keyIndex + 1] = value
    return newBuffer
}

private fun <K, V> Array<Any?>.replaceNodeWithEntry(nodeIndex: Int, keyIndex: Int, key: K, value: V): Array<Any?> {
    val newBuffer = this.copyOf(this.size + 1)
    newBuffer.copyInto(newBuffer, nodeIndex + 2, nodeIndex + 1, this.size)
    newBuffer.copyInto(newBuffer, keyIndex + 2, keyIndex, nodeIndex)
    newBuffer[keyIndex] = key
    newBuffer[keyIndex + 1] = value
    return newBuffer
}

private fun Array<Any?>.replaceEntryWithNode(keyIndex: Int, nodeIndex: Int, newNode: TrieNode<*, *>): Array<Any?> {
    val newNodeIndex = nodeIndex - ENTRY_SIZE // place where to insert new node in the new buffer
    val newBuffer = arrayOfNulls<Any?>(this.size - ENTRY_SIZE + 1)
    this.copyInto(newBuffer, endIndex = keyIndex)
    this.copyInto(newBuffer, keyIndex, startIndex = keyIndex + ENTRY_SIZE, endIndex = nodeIndex)
    newBuffer[newNodeIndex] = newNode
    this.copyInto(newBuffer, newNodeIndex + 1, startIndex = nodeIndex, endIndex = this.size)
    return newBuffer
}

private fun Array<Any?>.removeEntryAtIndex(keyIndex: Int): Array<Any?> {
    val newBuffer = arrayOfNulls<Any?>(this.size - ENTRY_SIZE)
    this.copyInto(newBuffer, endIndex = keyIndex)
    this.copyInto(newBuffer, keyIndex, startIndex = keyIndex + ENTRY_SIZE, endIndex = this.size)
    return newBuffer
}

private fun Array<Any?>.removeNodeAtIndex(nodeIndex: Int): Array<Any?> {
    val newBuffer = arrayOfNulls<Any?>(this.size - 1)
    this.copyInto(newBuffer, endIndex = nodeIndex)
    this.copyInto(newBuffer, nodeIndex, startIndex = nodeIndex + 1, endIndex = this.size)
    return newBuffer
}


class TrieNode<K, V>(
    private var dataMap: Int,
    private var nodeMap: Int,
    buffer: Array<Any?>,
    private val ownedBy: MutabilityOwnership?
) : Iterable<Map.Entry<K, V>> {
    constructor(dataMap: Int, nodeMap: Int, buffer: Array<Any?>) : this(dataMap, nodeMap, buffer, null)

    internal var buffer: Array<Any?> = buffer
        private set

    /** Returns number of entries stored in this trie node (not counting subnodes) */
    internal fun entryCount(): Int = dataMap.countOneBits()

    // here and later:
    // positionMask — an int in form 2^n, i.e. having the single bit set, whose ordinal is a logical position in buffer


    /** Returns true if the data bit map has the bit specified by [positionMask] set, indicating there's a data entry
     * in the buffer at that position. */
    internal fun hasEntryAt(positionMask: Int): Boolean {
        return dataMap and positionMask != 0
    }

    /** Returns true if the node bit map has the bit specified by [positionMask] set, indicating there's a subtrie node
     *  in the buffer at that position. */
    private fun hasNodeAt(positionMask: Int): Boolean {
        return nodeMap and positionMask != 0
    }

    /** Gets the index in buffer of the data entry key corresponding to the position specified by [positionMask]. */
    internal fun entryKeyIndex(positionMask: Int): Int {
        return ENTRY_SIZE * (dataMap and (positionMask - 1)).countOneBits()
    }

    /** Gets the index in buffer of the subtrie node entry corresponding to the position specified by [positionMask]. */
    internal fun nodeIndex(positionMask: Int): Int {
        return buffer.size - 1 - (nodeMap and (positionMask - 1)).countOneBits()
    }

    /** Retrieves the buffer element at the given [keyIndex] as key of a data entry. */
    private fun keyAtIndex(keyIndex: Int): K {
        @Suppress("UNCHECKED_CAST")
        return buffer[keyIndex] as K
    }

    /** Retrieves the buffer element next to the given [keyIndex] as value of a data entry. */
    private fun valueAtKeyIndex(keyIndex: Int): V {
        @Suppress("UNCHECKED_CAST")
        return buffer[keyIndex + 1] as V
    }

    /** Retrieves the buffer element at the given [nodeIndex] as subtrie node. */
    internal fun nodeAtIndex(nodeIndex: Int): TrieNode<K, V> {
        @Suppress("UNCHECKED_CAST")
        return buffer[nodeIndex] as TrieNode<K, V>
    }

    private fun insertEntryAt(positionMask: Int, key: K, value: V): TrieNode<K, V> {
//        assert(!hasEntryAt(positionMask))

        val keyIndex = entryKeyIndex(positionMask)
        val newBuffer = buffer.insertEntryAtIndex(keyIndex, key, value)
        return TrieNode(dataMap or positionMask, nodeMap, newBuffer)
    }

    private fun mutableInsertEntryAt(positionMask: Int, key: K, value: V, owner: MutabilityOwnership): TrieNode<K, V> {
//        assert(!hasEntryAt(positionMask))
        val keyIndex = entryKeyIndex(positionMask)
        if (ownedBy === owner) {
            buffer = buffer.insertEntryAtIndex(keyIndex, key, value)
            dataMap = dataMap or positionMask
            return this
        }
        val newBuffer = buffer.insertEntryAtIndex(keyIndex, key, value)
        return TrieNode(dataMap or positionMask, nodeMap, newBuffer, owner)
    }

    private fun updateValueAtIndex(keyIndex: Int, value: V): TrieNode<K, V> {
//        assert(buffer[keyIndex + 1] !== value)

        val newBuffer = buffer.copyOf()
        newBuffer[keyIndex + 1] = value
        return TrieNode(dataMap, nodeMap, newBuffer)
    }

    private fun mutableUpdateValueAtIndex(keyIndex: Int, value: V, owner: MutabilityOwnership): TrieNode<K, V> {
//        assert(buffer[keyIndex + 1] !== value)
        // If the [mutator] is exclusive owner of this node, update value at specified index in-place.
        if (ownedBy === owner) {
            buffer[keyIndex + 1] = value
            return this
        }
        // Create new node with updated value at specified index.
        val newBuffer = buffer.copyOf()
        newBuffer[keyIndex + 1] = value
        return TrieNode(dataMap, nodeMap, newBuffer, owner)
    }

    private fun updateNodeAtIndex(nodeIndex: Int, positionMask: Int, newNode: TrieNode<K, V>): TrieNode<K, V> {
//        assert(buffer[nodeIndex] !== newNode)
        val newNodeBuffer = newNode.buffer
        if (newNodeBuffer.size == 2 && newNode.nodeMap == 0) {
            if (buffer.size == 1) {
//                assert(dataMap == 0 && nodeMap xor positionMask == 0)
                newNode.dataMap = nodeMap
                return newNode
            }

            val keyIndex = entryKeyIndex(positionMask)
            val newBuffer = buffer.replaceNodeWithEntry(nodeIndex, keyIndex, newNodeBuffer[0], newNodeBuffer[1])
            return TrieNode(
                dataMap xor positionMask,
                nodeMap xor positionMask,
                newBuffer
            )
        }

        val newBuffer = buffer.copyOf(buffer.size)
        newBuffer[nodeIndex] = newNode
        return TrieNode(dataMap, nodeMap, newBuffer)
    }

    /** The given [newNode] must not be a part of any persistent map instance. */
    private fun mutableUpdateNodeAtIndex(
        nodeIndex: Int,
        newNode: TrieNode<K, V>,
        owner: MutabilityOwnership
    ): TrieNode<K, V> {
        assert(newNode.ownedBy === owner)
//        assert(buffer[nodeIndex] !== newNode)

        // nodes (including collision nodes) that have only one entry are upped if they have no siblings
        if (buffer.size == 1 && newNode.buffer.size == ENTRY_SIZE && newNode.nodeMap == 0) {
//          assert(dataMap == 0 && nodeMap xor positionMask == 0)
            newNode.dataMap = nodeMap
            return newNode
        }

        if (ownedBy === owner) {
            buffer[nodeIndex] = newNode
            return this
        }
        val newBuffer = buffer.copyOf()
        newBuffer[nodeIndex] = newNode
        return TrieNode(dataMap, nodeMap, newBuffer, owner)
    }

    private fun removeNodeAtIndex(nodeIndex: Int, positionMask: Int): TrieNode<K, V>? {
//        assert(hasNodeAt(positionMask))
        if (buffer.size == 1) return null

        val newBuffer = buffer.removeNodeAtIndex(nodeIndex)
        return TrieNode(dataMap, nodeMap xor positionMask, newBuffer)
    }

    private fun mutableRemoveNodeAtIndex(
        nodeIndex: Int,
        positionMask: Int,
        owner: MutabilityOwnership
    ): TrieNode<K, V>? {
//        assert(hasNodeAt(positionMask))
        if (buffer.size == 1) return null

        if (ownedBy === owner) {
            buffer = buffer.removeNodeAtIndex(nodeIndex)
            nodeMap = nodeMap xor positionMask
            return this
        }
        val newBuffer = buffer.removeNodeAtIndex(nodeIndex)
        return TrieNode(dataMap, nodeMap xor positionMask, newBuffer, owner)
    }

    private fun bufferMoveEntryToNode(
        keyIndex: Int, positionMask: Int, newKeyHash: Int,
        newKey: K, newValue: V, shift: Int, owner: MutabilityOwnership?
    ): Array<Any?> {
        val storedKey = keyAtIndex(keyIndex)
        val storedKeyHash = storedKey.hashCode()
        val storedValue = valueAtKeyIndex(keyIndex)
        val newNode = makeNode(
            storedKeyHash, storedKey, storedValue,
            newKeyHash, newKey, newValue, shift + LOG_MAX_BRANCHING_FACTOR, owner
        )

        val nodeIndex = nodeIndex(positionMask) + 1 // place where to insert new node in the current buffer

        return buffer.replaceEntryWithNode(keyIndex, nodeIndex, newNode)
    }

    private fun moveEntryToNode(
        keyIndex: Int, positionMask: Int, newKeyHash: Int,
        newKey: K, newValue: V, shift: Int
    ): TrieNode<K, V> {
//        assert(hasEntryAt(positionMask))
//        assert(!hasNodeAt(positionMask))

        val newBuffer = bufferMoveEntryToNode(keyIndex, positionMask, newKeyHash, newKey, newValue, shift, null)
        return TrieNode(dataMap xor positionMask, nodeMap or positionMask, newBuffer)
    }

    private fun mutableMoveEntryToNode(
        keyIndex: Int,
        positionMask: Int,
        newKeyHash: Int,
        newKey: K,
        newValue: V,
        shift: Int,
        owner: MutabilityOwnership,
    ): TrieNode<K, V> {
//        assert(hasEntryAt(positionMask))
//        assert(!hasNodeAt(positionMask))
        if (ownedBy === owner) {
            buffer = bufferMoveEntryToNode(keyIndex, positionMask, newKeyHash, newKey, newValue, shift, owner)
            dataMap = dataMap xor positionMask
            nodeMap = nodeMap or positionMask
            return this
        }
        val newBuffer = bufferMoveEntryToNode(keyIndex, positionMask, newKeyHash, newKey, newValue, shift, owner)
        return TrieNode(dataMap xor positionMask, nodeMap or positionMask, newBuffer, owner)
    }

    /** Creates a new TrieNode for holding two given key value entries */
    private fun makeNode(
        keyHash1: Int, key1: K, value1: V,
        keyHash2: Int, key2: K, value2: V, shift: Int, owner: MutabilityOwnership?
    ): TrieNode<K, V> {
        if (shift > MAX_SHIFT) {
//            assert(key1 != key2)
            // when two key hashes are entirely equal: the last level subtrie node stores them just as unordered list
            return TrieNode(0, 0, arrayOf(key1, value1, key2, value2), owner)
        }

        val setBit1 = indexSegment(keyHash1, shift)
        val setBit2 = indexSegment(keyHash2, shift)

        if (setBit1 != setBit2) {
            val nodeBuffer = if (setBit1 < setBit2) {
                arrayOf(key1, value1, key2, value2)
            } else {
                arrayOf(key2, value2, key1, value1)
            }
            return TrieNode((1 shl setBit1) or (1 shl setBit2), 0, nodeBuffer, owner)
        }
        // hash segments at the given shift are equal: move these entries into the subtrie
        val node = makeNode(keyHash1, key1, value1, keyHash2, key2, value2, shift + LOG_MAX_BRANCHING_FACTOR, owner)
        return TrieNode(0, 1 shl setBit1, arrayOf<Any?>(node), owner)
    }

    private fun removeEntryAtIndex(keyIndex: Int, positionMask: Int): TrieNode<K, V>? {
//        assert(hasEntryAt(positionMask))
        if (buffer.size == ENTRY_SIZE) return null
        val newBuffer = buffer.removeEntryAtIndex(keyIndex)
        return TrieNode(dataMap xor positionMask, nodeMap, newBuffer)
    }

    private fun mutableRemoveEntryAtIndex(
        keyIndex: Int,
        positionMask: Int,
        owner: MutabilityOwnership
    ): TrieNode<K, V>? {
//        assert(hasEntryAt(positionMask))
        if (buffer.size == ENTRY_SIZE) return null

        if (ownedBy === owner) {
            buffer = buffer.removeEntryAtIndex(keyIndex)
            dataMap = dataMap xor positionMask
            return this
        }
        val newBuffer = buffer.removeEntryAtIndex(keyIndex)
        return TrieNode(dataMap xor positionMask, nodeMap, newBuffer, owner)
    }

    private fun collisionRemoveEntryAtIndex(i: Int): TrieNode<K, V>? {
        if (buffer.size == ENTRY_SIZE) return null
        val newBuffer = buffer.removeEntryAtIndex(i)
        return TrieNode(0, 0, newBuffer)
    }

    private fun mutableCollisionRemoveEntryAtIndex(i: Int, owner: MutabilityOwnership): TrieNode<K, V>? {
        if (buffer.size == ENTRY_SIZE) return null

        if (ownedBy === owner) {
            buffer = buffer.removeEntryAtIndex(i)
            return this
        }
        val newBuffer = buffer.removeEntryAtIndex(i)
        return TrieNode(0, 0, newBuffer, owner)
    }

    private fun collisionKeyIndex(key: Any?): Int {
        for (i in 0 until buffer.size step ENTRY_SIZE) {
            if (key == keyAtIndex(i)) return i
        }
        return -1
    }

    private fun collisionContainsKey(key: K): Boolean {
        return collisionKeyIndex(key) != -1
    }

    private fun collisionGet(key: K): V? {
        val keyIndex = collisionKeyIndex(key)
        return if (keyIndex != -1) valueAtKeyIndex(keyIndex) else null
    }

    private fun collisionPut(key: K, value: V): TrieNode<K, V> {
        val keyIndex = collisionKeyIndex(key)
        if (keyIndex != -1) {
            if (value === valueAtKeyIndex(keyIndex)) {
                return this
            }
            val newBuffer = buffer.copyOf()
            newBuffer[keyIndex + 1] = value
            return TrieNode<K, V>(0, 0, newBuffer)
        }
        val newBuffer = buffer.insertEntryAtIndex(0, key, value)
        return TrieNode<K, V>(0, 0, newBuffer)
    }

    private fun mutableCollisionPut(key: K, value: V, owner: MutabilityOwnership): TrieNode<K, V> {
        // Check if there is an entry with the specified key.
        val keyIndex = collisionKeyIndex(key)
        if (keyIndex != -1) { // found entry with the specified key
            // If the [mutator] is exclusive owner of this node, update value of the entry in-place.
            if (ownedBy === owner) {
                buffer[keyIndex + 1] = value
                return this
            }

            // Structural change due to node replacement.
            // Create new node with updated entry value.
            val newBuffer = buffer.copyOf()
            newBuffer[keyIndex + 1] = value
            return TrieNode(0, 0, newBuffer, owner)
        }
        // Create new collision node with the specified entry added to it.
        val newBuffer = buffer.insertEntryAtIndex(0, key, value)
        return TrieNode(0, 0, newBuffer, owner)
    }

    private fun collisionRemove(key: K): Pair<TrieNode<K, V>?, Boolean> {
        val keyIndex = collisionKeyIndex(key)
        if (keyIndex != -1) {
            return collisionRemoveEntryAtIndex(keyIndex) to true
        }
        return this to false
    }

    private fun mutableCollisionRemove(key: K, owner: MutabilityOwnership): Pair<TrieNode<K, V>?, Boolean> {
        val keyIndex = collisionKeyIndex(key)
        if (keyIndex != -1) {
            return mutableCollisionRemoveEntryAtIndex(keyIndex, owner) to true
        }
        return this to false
    }

    private fun mutableCollisionRemoveAndGetValue(key: K, owner: MutabilityOwnership): Pair<TrieNode<K, V>?, V?> {
        val keyIndex = collisionKeyIndex(key)
        if (keyIndex != -1) {
            val value = valueAtKeyIndex(keyIndex)
            return mutableCollisionRemoveEntryAtIndex(keyIndex, owner) to value
        }
        return this to null
    }

    private fun mutableCollisionPutAll(otherNode: TrieNode<K, V>, owner: MutabilityOwnership): TrieNode<K, V> {
        assert(nodeMap == 0)
        assert(dataMap == 0)
        assert(otherNode.nodeMap == 0)
        assert(otherNode.dataMap == 0)
        val tempBuffer = this.buffer.copyOf(newSize = this.buffer.size + otherNode.buffer.size)
        var i = this.buffer.size
        for (j in 0 until otherNode.buffer.size step ENTRY_SIZE) {
            @Suppress("UNCHECKED_CAST")
            if (!this.collisionContainsKey(otherNode.buffer[j] as K)) {
                tempBuffer[i] = otherNode.buffer[j]
                tempBuffer[i + 1] = otherNode.buffer[j + 1]
                i += ENTRY_SIZE
            }
        }

        return when (val newSize = i) {
            this.buffer.size -> this
            otherNode.buffer.size -> otherNode
            tempBuffer.size -> TrieNode(0, 0, tempBuffer, owner)
            else -> TrieNode(0, 0, tempBuffer.copyOf(newSize), owner)
        }
    }

    /**
     * Updates the cell of this node at [positionMask] with entries from the cell of [otherNode] at [positionMask].
     */
    private fun mutablePutAllFromOtherNodeCell(
        otherNode: TrieNode<K, V>,
        positionMask: Int,
        shift: Int,
        owner: MutabilityOwnership
    ): TrieNode<K, V> = when {
        this.hasNodeAt(positionMask) -> {
            val targetNode = this.nodeAtIndex(nodeIndex(positionMask))
            when {
                otherNode.hasNodeAt(positionMask) -> {
                    val otherTargetNode = otherNode.nodeAtIndex(otherNode.nodeIndex(positionMask))
                    targetNode.mutablePutAll(otherTargetNode, shift + LOG_MAX_BRANCHING_FACTOR, owner)
                }

                otherNode.hasEntryAt(positionMask) -> {
                    val keyIndex = otherNode.entryKeyIndex(positionMask)
                    val key = otherNode.keyAtIndex(keyIndex)
                    val value = otherNode.valueAtKeyIndex(keyIndex)
                    targetNode.mutablePut(key.hashCode(), key, value, shift + LOG_MAX_BRANCHING_FACTOR, owner)
                }

                else -> targetNode
            }
        }

        otherNode.hasNodeAt(positionMask) -> {
            val otherTargetNode = otherNode.nodeAtIndex(otherNode.nodeIndex(positionMask))
            when {
                this.hasEntryAt(positionMask) -> {
                    // if otherTargetNode already has a value associated with the key, do not put this entry
                    val keyIndex = this.entryKeyIndex(positionMask)
                    val key = this.keyAtIndex(keyIndex)
                    if (otherTargetNode.containsKey(key.hashCode(), key, shift + LOG_MAX_BRANCHING_FACTOR)) {
                        otherTargetNode
                    } else {
                        val value = this.valueAtKeyIndex(keyIndex)
                        otherTargetNode.mutablePut(key.hashCode(), key, value, shift + LOG_MAX_BRANCHING_FACTOR, owner)
                    }
                }

                else -> otherTargetNode
            }
        }

        else -> { // two entries, and they are not equal by key. See (**) in mutablePutAll
            val thisKeyIndex = this.entryKeyIndex(positionMask)
            val thisKey = this.keyAtIndex(thisKeyIndex)
            val thisValue = this.valueAtKeyIndex(thisKeyIndex)
            val otherKeyIndex = otherNode.entryKeyIndex(positionMask)
            val otherKey = otherNode.keyAtIndex(otherKeyIndex)
            val otherValue = otherNode.valueAtKeyIndex(otherKeyIndex)
            makeNode(
                thisKey.hashCode(),
                thisKey,
                thisValue,
                otherKey.hashCode(),
                otherKey,
                otherValue,
                shift + LOG_MAX_BRANCHING_FACTOR,
                owner
            )
        }
    }

    private fun elementsIdentityEquals(otherNode: TrieNode<K, V>): Boolean {
        if (this === otherNode) return true
        if (nodeMap != otherNode.nodeMap) return false
        if (dataMap != otherNode.dataMap) return false
        for (i in 0 until buffer.size) {
            if (buffer[i] !== otherNode.buffer[i]) return false
        }
        return true
    }

    private fun containsKey(keyHash: Int, key: K, shift: Int): Boolean {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)
        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            return key == keyAtIndex(entryKeyIndex(keyPositionMask))
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val targetNode = nodeAtIndex(nodeIndex(keyPositionMask))
            if (shift == MAX_SHIFT) {
                return targetNode.collisionContainsKey(key)
            }
            return targetNode.containsKey(keyHash, key, shift + LOG_MAX_BRANCHING_FACTOR)
        }

        // key is absent
        return false
    }

    private fun get(keyHash: Int, key: K, shift: Int): V? {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = entryKeyIndex(keyPositionMask)

            if (key == keyAtIndex(keyIndex)) {
                return valueAtKeyIndex(keyIndex)
            }
            return null
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val targetNode = nodeAtIndex(nodeIndex(keyPositionMask))
            if (shift == MAX_SHIFT) {
                return targetNode.collisionGet(key)
            }
            return targetNode.get(keyHash, key, shift + LOG_MAX_BRANCHING_FACTOR)
        }

        // key is absent
        return null
    }

    private fun mutablePutAll(otherNode: TrieNode<K, V>, shift: Int, owner: MutabilityOwnership): TrieNode<K, V> {
        if (this === otherNode) {
            return this
        }
        // the collision case
        if (shift > MAX_SHIFT) {
            return mutableCollisionPutAll(otherNode, owner)
        }

        // new nodes are where either of the old ones were
        var newNodeMap = nodeMap or otherNode.nodeMap
        // entries stay being entries only if one bits were in exactly one of input nodes
        // but not in the new data nodes
        var newDataMap = dataMap xor otherNode.dataMap and newNodeMap.inv()
        // (**) now, this is tricky: we have a number of entry-entry pairs and we don't know yet whether
        // they result in an entry (if keys are equal) or a new node (if they are not)
        // but we want to keep it to single allocation, so we check and mark equal ones here
        (dataMap and otherNode.dataMap).forEachOneBit { positionMask, _ ->
            val leftKey = this.keyAtIndex(this.entryKeyIndex(positionMask))
            val rightKey = otherNode.keyAtIndex(otherNode.entryKeyIndex(positionMask))
            // if they are equal, put them in the data map
            if (leftKey == rightKey) newDataMap = newDataMap or positionMask
            // if they are not, put them in the node map
            else newNodeMap = newNodeMap or positionMask
            // we can use this later to skip calling equals() again
        }
        check(newNodeMap and newDataMap == 0)
        val mutableNode = when {
            this.ownedBy == owner && this.dataMap == newDataMap && this.nodeMap == newNodeMap -> this
            else -> {
                val newBuffer = arrayOfNulls<Any>(newDataMap.countOneBits() * ENTRY_SIZE + newNodeMap.countOneBits())
                TrieNode(newDataMap, newNodeMap, newBuffer)
            }
        }
        newNodeMap.forEachOneBit { positionMask, index ->
            val newNodeIndex = mutableNode.buffer.size - 1 - index
            mutableNode.buffer[newNodeIndex] = mutablePutAllFromOtherNodeCell(otherNode, positionMask, shift, owner)
        }
        newDataMap.forEachOneBit { positionMask, index ->
            val newKeyIndex = index * ENTRY_SIZE
            when {
                !otherNode.hasEntryAt(positionMask) -> {
                    val oldKeyIndex = this.entryKeyIndex(positionMask)
                    mutableNode.buffer[newKeyIndex] = this.keyAtIndex(oldKeyIndex)
                    mutableNode.buffer[newKeyIndex + 1] = this.valueAtKeyIndex(oldKeyIndex)
                }
                // there is either only one entry in otherNode, or
                // both entries are here => they are equal, see ** above
                // so just overwrite that
                else -> {
                    val oldKeyIndex = otherNode.entryKeyIndex(positionMask)
                    mutableNode.buffer[newKeyIndex] = otherNode.keyAtIndex(oldKeyIndex)
                    mutableNode.buffer[newKeyIndex + 1] = otherNode.valueAtKeyIndex(oldKeyIndex)
                }
            }
        }
        return when {
            this.elementsIdentityEquals(mutableNode) -> this
            otherNode.elementsIdentityEquals(mutableNode) -> otherNode
            else -> mutableNode
        }
    }

    fun remove(keyHash: Int, key: K, shift: Int): Pair<TrieNode<K, V>?, Boolean> {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = entryKeyIndex(keyPositionMask)

            if (key == keyAtIndex(keyIndex)) {
                return removeEntryAtIndex(keyIndex, keyPositionMask) to true
            }
            return this to false
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val nodeIndex = nodeIndex(keyPositionMask)

            val targetNode = nodeAtIndex(nodeIndex)
            val (newNode, hasChanged) = if (shift == MAX_SHIFT) {
                targetNode.collisionRemove(key)
            } else {
                targetNode.remove(keyHash, key, shift + LOG_MAX_BRANCHING_FACTOR)
            }
            return replaceNode(targetNode, newNode, nodeIndex, keyPositionMask) to hasChanged
        }

        // key is absent
        return this to false
    }

    private fun replaceNode(targetNode: TrieNode<K, V>, newNode: TrieNode<K, V>?, nodeIndex: Int, positionMask: Int) =
        when {
            newNode == null ->
                removeNodeAtIndex(nodeIndex, positionMask)

            targetNode !== newNode ->
                updateNodeAtIndex(nodeIndex, positionMask, newNode)

            else ->
                this
        }

    fun put(keyHash: Int, key: K, value: @UnsafeVariance V, shift: Int): TrieNode<K, V> {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = entryKeyIndex(keyPositionMask)

            if (key == keyAtIndex(keyIndex)) {
                if (valueAtKeyIndex(keyIndex) === value) return this

                return updateValueAtIndex(keyIndex, value)
            }
            return moveEntryToNode(keyIndex, keyPositionMask, keyHash, key, value, shift)
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val nodeIndex = nodeIndex(keyPositionMask)

            val targetNode = nodeAtIndex(nodeIndex)
            val putResult = if (shift == MAX_SHIFT) {
                targetNode.collisionPut(key, value)
            } else {
                targetNode.put(keyHash, key, value, shift + LOG_MAX_BRANCHING_FACTOR)
            }
            return replaceNode(targetNode, putResult, nodeIndex, keyPositionMask)!!
        }

        // no entry at this key hash segment
        return insertEntryAt(keyPositionMask, key, value)
    }


    private fun mutablePut(
        keyHash: Int,
        key: K,
        value: @UnsafeVariance V,
        shift: Int,
        owner: MutabilityOwnership
    ): TrieNode<K, V> {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = entryKeyIndex(keyPositionMask)

            if (key == keyAtIndex(keyIndex)) {
                if (valueAtKeyIndex(keyIndex) === value) {
                    return this
                }

                return mutableUpdateValueAtIndex(keyIndex, value, owner)
            }
            return mutableMoveEntryToNode(keyIndex, keyPositionMask, keyHash, key, value, shift, owner)
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val nodeIndex = nodeIndex(keyPositionMask)

            val targetNode = nodeAtIndex(nodeIndex)
            val newNode = if (shift == MAX_SHIFT) {
                targetNode.mutableCollisionPut(key, value, owner)
            } else {
                targetNode.mutablePut(keyHash, key, value, shift + LOG_MAX_BRANCHING_FACTOR, owner)
            }
            if (targetNode === newNode) {
                return this
            }
            return mutableUpdateNodeAtIndex(nodeIndex, newNode, owner)
        }

        // key is absent
        return mutableInsertEntryAt(keyPositionMask, key, value, owner)
    }

    private fun mutableRemove(
        keyHash: Int,
        key: K,
        shift: Int,
        owner: MutabilityOwnership
    ): Pair<TrieNode<K, V>?, Boolean> {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = entryKeyIndex(keyPositionMask)

            if (key == keyAtIndex(keyIndex)) {
                return mutableRemoveEntryAtIndex(keyIndex, keyPositionMask, owner) to true
            }
            return this to false
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val nodeIndex = nodeIndex(keyPositionMask)

            val targetNode = nodeAtIndex(nodeIndex)
            val (newNode, hasChanged) = if (shift == MAX_SHIFT) {
                targetNode.mutableCollisionRemove(key, owner)
            } else {
                targetNode.mutableRemove(keyHash, key, shift + LOG_MAX_BRANCHING_FACTOR, owner)
            }
            return mutableReplaceNode(targetNode, newNode, nodeIndex, keyPositionMask, owner) to hasChanged
        }

        // key is absent
        return this to false
    }

    private fun mutableRemoveAndGetValue(
        keyHash: Int,
        key: K,
        shift: Int,
        owner: MutabilityOwnership
    ): Pair<TrieNode<K, V>?, V?> {
        val keyPositionMask = 1 shl indexSegment(keyHash, shift)

        if (hasEntryAt(keyPositionMask)) { // key is directly in buffer
            val keyIndex = entryKeyIndex(keyPositionMask)

            if (key == keyAtIndex(keyIndex)) {
                val value = valueAtKeyIndex(keyIndex)
                return mutableRemoveEntryAtIndex(keyIndex, keyPositionMask, owner) to value
            }
            return this to null
        }
        if (hasNodeAt(keyPositionMask)) { // key is in node
            val nodeIndex = nodeIndex(keyPositionMask)

            val targetNode = nodeAtIndex(nodeIndex)
            val (newNode, value) = if (shift == MAX_SHIFT) {
                targetNode.mutableCollisionRemoveAndGetValue(key, owner)
            } else {
                targetNode.mutableRemoveAndGetValue(keyHash, key, shift + LOG_MAX_BRANCHING_FACTOR, owner)
            }
            return mutableReplaceNode(targetNode, newNode, nodeIndex, keyPositionMask, owner) to value
        }

        // key is absent
        return this to null
    }

    private fun mutableReplaceNode(
        targetNode: TrieNode<K, V>,
        newNode: TrieNode<K, V>?,
        nodeIndex: Int,
        positionMask: Int,
        owner: MutabilityOwnership
    ) = when {
        newNode == null ->
            mutableRemoveNodeAtIndex(nodeIndex, positionMask, owner)

        targetNode !== newNode ->
            mutableUpdateNodeAtIndex(nodeIndex, newNode, owner)

        else -> this
    }

    // For testing trie structure
    internal fun accept(visitor: (node: TrieNode<K, V>, shift: Int, hash: Int, dataMap: Int, nodeMap: Int) -> Unit) {
        accept(visitor, 0, 0)
    }

    private fun accept(
        visitor: (node: TrieNode<K, V>, shift: Int, hash: Int, dataMap: Int, nodeMap: Int) -> Unit,
        hash: Int,
        shift: Int
    ) {
        visitor(this, shift, hash, dataMap, nodeMap)

        var nodePositions = nodeMap
        while (nodePositions != 0) {
            val mask = nodePositions.takeLowestOneBit()
//            assert(hasNodeAt(mask))

            val hashSegment = mask.countTrailingZeroBits()

            val childNode = nodeAtIndex(nodeIndex(mask))
            childNode.accept(visitor, hash + (hashSegment shl shift), shift + LOG_MAX_BRANCHING_FACTOR)

            nodePositions -= mask
        }
    }

    private fun <K1, V1> equalsWith(that: TrieNode<K1, V1>, equalityComparator: (V, V1) -> Boolean): Boolean {
        if (this === that) return true
        if (dataMap != that.dataMap || nodeMap != that.nodeMap) return false
        if (dataMap == 0 && nodeMap == 0) { // collision node
            if (buffer.size != that.buffer.size) return false
            return (0 until buffer.size step ENTRY_SIZE).all { i ->
                val thatKey = that.keyAtIndex(i)
                val thatValue = that.valueAtKeyIndex(i)
                val keyIndex = collisionKeyIndex(thatKey)
                if (keyIndex != -1) {
                    val value = valueAtKeyIndex(keyIndex)
                    equalityComparator(value, thatValue)
                } else false
            }
        }

        val valueSize = dataMap.countOneBits() * ENTRY_SIZE
        for (i in 0 until valueSize step ENTRY_SIZE) {
            if (keyAtIndex(i) != that.keyAtIndex(i)) return false
            if (!equalityComparator(valueAtKeyIndex(i), that.valueAtKeyIndex(i))) return false
        }
        for (i in valueSize until buffer.size) {
            if (!nodeAtIndex(i).equalsWith(that.nodeAtIndex(i), equalityComparator)) return false
        }
        return true
    }

    val keys: Sequence<K> get() = UPersistentHashMapKeysIterator(this).asSequence()

    fun containsKey(key: K): Boolean = containsKey(key.hashCode(), key, 0)

    operator fun get(key: K) = get(key.hashCode(), key, 0)

    operator fun contains(key: K) = containsKey(key)

    fun put(key: K, value: V, owner: MutabilityOwnership): TrieNode<K, V> =
        mutablePut(key.hashCode(), key, value, 0, owner)

    fun remove(key: K, owner: MutabilityOwnership): TrieNode<K, V> =
        removeWithChangeInfo(key, owner).first

    @Suppress("UNCHECKED_CAST")
    fun removeWithChangeInfo(key: K, owner: MutabilityOwnership): Pair<TrieNode<K, V>, Boolean> {
        val (node, hasChanged) = mutableRemove(key.hashCode(), key, 0, owner)
        return (node ?: EMPTY as TrieNode<K, V>) to hasChanged
    }

    fun removeAndGetValue(key: K, owner: MutabilityOwnership): Pair<TrieNode<K, V>, V?> {
        val (node, value) = mutableRemoveAndGetValue(key.hashCode(), key, 0, owner)
        @Suppress("UNCHECKED_CAST")
        return (node ?: EMPTY as TrieNode<K, V>) to value
    }

    fun putAll(otherNode: TrieNode<K, V>, owner: MutabilityOwnership): TrieNode<K, V> {
        return mutablePutAll(otherNode, 0, owner)
    }

    @Suppress("UNCHECKED_CAST")
    fun clear() = EMPTY as TrieNode<K, V>

    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?): Boolean {
        other as? TrieNode<K, V> ?: return false
        return this.equalsWith(other) { v1, v2 -> v1 == v2 }
    }

    override fun toString(): String =
        iterator().asSequence()
            .joinToString(separator = "\n", prefix = "{", postfix = "}") { "${it.key} -> ${it.value}" }

    override fun hashCode(): Int = sumOf { it.hashCode() }

    override fun iterator(): Iterator<Map.Entry<K, V>> = UPersistentHashMapEntriesIterator(this)

    companion object {
        internal val EMPTY = TrieNode<Nothing, Nothing>(0, 0, emptyArray())
    }
}
