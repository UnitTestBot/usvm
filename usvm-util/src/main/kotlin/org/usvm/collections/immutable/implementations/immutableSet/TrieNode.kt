/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableSet

import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.internal.forEachOneBit

typealias UPersistentHashSet<E> = TrieNode<E>


internal const val MAX_BRANCHING_FACTOR = 32
internal const val LOG_MAX_BRANCHING_FACTOR = 5
internal const val MAX_BRANCHING_FACTOR_MINUS_ONE = MAX_BRANCHING_FACTOR - 1
internal const val MAX_SHIFT = 30

/**
 * Gets trie index segment of the specified [index] at the level specified by [shift].
 *
 * `shift` equal to zero corresponds to the root level.
 * For each lower level `shift` increments by [LOG_MAX_BRANCHING_FACTOR].
 */
internal fun indexSegment(index: Int, shift: Int): Int =
        (index shr shift) and MAX_BRANCHING_FACTOR_MINUS_ONE


private fun <E> Array<Any?>.addElementAtIndex(index: Int, element: E): Array<Any?> {
    val newBuffer = arrayOfNulls<Any?>(this.size + 1)
    this.copyInto(newBuffer, endIndex = index)
    this.copyInto(newBuffer, index + 1, index, this.size)
    newBuffer[index] = element
    return newBuffer
}

private fun Array<Any?>.removeCellAtIndex(cellIndex: Int): Array<Any?> {
    val newBuffer = arrayOfNulls<Any?>(this.size - 1)
    this.copyInto(newBuffer, endIndex = cellIndex)
    this.copyInto(newBuffer, cellIndex, cellIndex + 1, this.size)
    return newBuffer
}

/**
 * Writes all elements from [this] to [newArray], starting with [newArrayOffset], filtering
 * on the fly using [predicate]. By default filters out [TrieNode.EMPTY] instances
 *
 * return number of elements written to [newArray]
 **/
private inline fun Array<Any?>.filterTo(
    newArray: Array<Any?>,
    newArrayOffset: Int = 0,
    predicate: (Any?) -> Boolean = { it !== TrieNode.EMPTY },
): Int {
    var i = 0
    var j = 0
    while (i < size) {
        assert(j <= i) // this is extremely important if newArray === this
        val e = this[i]
        if (predicate(e)) {
            newArray[newArrayOffset + j] = this[i]
            ++j
            assert(newArrayOffset + j <= newArray.size)
        }
        ++i
    }
    return j
}

class TrieNode<E>(
        private var bitmap: Int,
        internal var buffer: Array<Any?>,
        private var ownedBy: MutabilityOwnership?
) : Iterable<E> {

    constructor(bitmap: Int, buffer: Array<Any?>) : this(bitmap, buffer, null)

    // here and later:
    // positionMask — an int in form 2^n, i.e. having the single bit set, whose ordinal is a logical position in buffer

    private fun hasNoCellAt(positionMask: Int): Boolean {
        return bitmap and positionMask == 0
    }

    internal fun indexOfCellAt(positionMask: Int): Int {
        return (bitmap and (positionMask - 1)).countOneBits()
    }

    private fun elementAtIndex(index: Int): E {
        @Suppress("UNCHECKED_CAST")
        return buffer[index] as E
    }

    private fun nodeAtIndex(index: Int): TrieNode<E> {
        @Suppress("UNCHECKED_CAST")
        return buffer[index] as TrieNode<E>
    }

    private fun addElementAt(positionMask: Int, element: E, owner: MutabilityOwnership?): TrieNode<E> {
//        assert(hasNoCellAt(positionMask))

        val index = indexOfCellAt(positionMask)
        val newBitmap = bitmap or positionMask
        val newBuffer = buffer.addElementAtIndex(index, element)
        return setProperties(newBitmap, newBuffer, owner)
    }

    private fun setProperties(newBitmap: Int, newBuffer: Array<Any?>, owner: MutabilityOwnership?): TrieNode<E> {
        if (ownedBy != null && ownedBy === owner) {
            bitmap = newBitmap
            buffer = newBuffer
            return this
        }
        return TrieNode(newBitmap, newBuffer, owner)
    }

    /** The given [newNode] must not be a part of any persistent set instance. */
    private fun canonicalizeNodeAtIndex(nodeIndex: Int, newNode: TrieNode<E>, owner: MutabilityOwnership?): TrieNode<E> {
//        assert(buffer[nodeIndex] !== newNode)
        val cell: Any?

        val newNodeBuffer = newNode.buffer
        if (newNodeBuffer.size == 1 && newNodeBuffer[0] !is TrieNode<*>) {
            if (buffer.size == 1) {
                newNode.bitmap = bitmap
                return newNode
            }
            cell = newNodeBuffer[0]
        } else {
            cell = newNode
        }

        return setCellAtIndex(nodeIndex, cell, owner)
    }

    private fun setCellAtIndex(cellIndex: Int, newCell: Any?, owner: MutabilityOwnership?): TrieNode<E> {
        if (ownedBy != null && ownedBy === owner) {
            buffer[cellIndex] = newCell
            return this
        }
        val newBuffer = buffer.copyOf()
        newBuffer[cellIndex] = newCell
        return TrieNode(bitmap, newBuffer, owner)
    }

    private fun makeNodeAtIndex(
        elementIndex: Int,
        newElementHash: Int,
        newElement: E,
        shift: Int,
        owner: MutabilityOwnership?,
    ): TrieNode<E> {
        val storedElement = elementAtIndex(elementIndex)
        return makeNode(storedElement.hashCode(), storedElement,
                newElementHash, newElement, shift + LOG_MAX_BRANCHING_FACTOR, owner)
    }

    private fun moveElementToNode(
        elementIndex: Int,
        newElementHash: Int,
        newElement: E,
        shift: Int,
        owner: MutabilityOwnership?
    ): TrieNode<E> {
        val node = makeNodeAtIndex(elementIndex, newElementHash, newElement, shift, owner)
        return setCellAtIndex(elementIndex, node, owner)
    }

    private fun makeNode(elementHash1: Int, element1: E, elementHash2: Int, element2: E,
                         shift: Int, owner: MutabilityOwnership?): TrieNode<E> {
        if (shift > MAX_SHIFT) {
//            assert(element1 != element2)
            // when two element hashes are entirely equal: the last level subtrie node stores them just as unordered list
            return TrieNode<E>(0, arrayOf(element1, element2), owner)
        }

        val setBit1 = indexSegment(elementHash1, shift)
        val setBit2 = indexSegment(elementHash2, shift)

        if (setBit1 != setBit2) {
            val nodeBuffer = if (setBit1 < setBit2) {
                arrayOf<Any?>(element1, element2)
            } else {
                arrayOf<Any?>(element2, element1)
            }
            return TrieNode((1 shl setBit1) or (1 shl setBit2), nodeBuffer, owner)
        }
        // hash segments at the given shift are equal: move these elements into the subtrie
        val node = makeNode(elementHash1, element1, elementHash2, element2, shift + LOG_MAX_BRANCHING_FACTOR, owner)
        return TrieNode<E>(1 shl setBit1, arrayOf(node), owner)
    }


    private fun removeCellAtIndex(cellIndex: Int, positionMask: Int, owner: MutabilityOwnership?): TrieNode<E> {
//        assert(!hasNoCellAt(positionMask))
//        assert(buffer.size > 1) can be false only for the root node

        val newBitmap = bitmap xor positionMask
        val newBuffer = buffer.removeCellAtIndex(cellIndex)
        return setProperties(newBitmap, newBuffer, owner)
    }

    private fun collisionRemoveElementAtIndex(i: Int, owner: MutabilityOwnership?): TrieNode<E> {
        val newBuffer = buffer.removeCellAtIndex(i)
        return setProperties(newBitmap = 0, newBuffer, owner)
    }

    private fun collisionContainsElement(element: E): Boolean {
        return buffer.contains(element)
    }

    private fun collisionAdd(element: E): TrieNode<E> {
        if (collisionContainsElement(element)) return this
        val newBuffer = buffer.addElementAtIndex(0, element)
        return setProperties(newBitmap = 0, newBuffer, owner = null)
    }

    private fun mutableCollisionAdd(element: E, owner: MutabilityOwnership): TrieNode<E> {
        if (collisionContainsElement(element)) return this
        val newBuffer = buffer.addElementAtIndex(0, element)
        return setProperties(newBitmap = 0, newBuffer, owner = owner)
    }

    private fun collisionRemove(element: E): TrieNode<E> {
        val index = buffer.indexOf(element)
        if (index != -1) {
            return collisionRemoveElementAtIndex(index, owner = null)
        }
        return this
    }

    private fun mutableCollisionRemove(element: E, owner: MutabilityOwnership): Pair<TrieNode<E>, Boolean> {
        val index = buffer.indexOf(element)
        if (index != -1) {
            return collisionRemoveElementAtIndex(index, owner) to true
        }
        return this to false
    }

    private fun mutableCollisionAddAll(otherNode: TrieNode<E>, owner: MutabilityOwnership): TrieNode<E> {
        if (this === otherNode) {
            return this
        }
        val tempBuffer = this.buffer.copyOf(newSize = this.buffer.size + otherNode.buffer.size)
        val totalWritten = otherNode.buffer.filterTo(tempBuffer, newArrayOffset = this.buffer.size) {
            @Suppress("UNCHECKED_CAST")
            !this.collisionContainsElement(it as E)
        }
        val totalSize = totalWritten + this.buffer.size
        if (totalSize == this.buffer.size) return this
        if (totalSize == otherNode.buffer.size) return otherNode

        val newBuffer = if (totalSize == tempBuffer.size) tempBuffer else tempBuffer.copyOf(newSize = totalSize)
        return setProperties(newBitmap = 0, newBuffer, owner)
    }

    private fun mutableCollisionRetainAll(otherNode: TrieNode<E>, owner: MutabilityOwnership): Any? {
        if (this === otherNode) {
            return this
        }
        val tempBuffer =
                if (owner === ownedBy) buffer
                else arrayOfNulls<Any?>(minOf(buffer.size, otherNode.buffer.size))
        val totalWritten = buffer.filterTo(tempBuffer) {
            @Suppress("UNCHECKED_CAST")
            otherNode.collisionContainsElement(it as E)
        }
        return when (totalWritten) {
            0 -> EMPTY
            1 -> tempBuffer[0]
            this.buffer.size -> this
            otherNode.buffer.size -> otherNode
            tempBuffer.size -> setProperties(newBitmap = 0, newBuffer = tempBuffer, owner)
            else -> setProperties(newBitmap = 0, newBuffer = tempBuffer.copyOf(newSize = totalWritten), owner)
        }
    }

    private fun mutableCollisionRemoveAll(otherNode: TrieNode<E>, owner: MutabilityOwnership): Any? {
        if (this === otherNode) {
            return EMPTY
        }
        val tempBuffer = if (owner === ownedBy) buffer else arrayOfNulls<Any?>(buffer.size)
        val totalWritten = buffer.filterTo(tempBuffer) {
            @Suppress("UNCHECKED_CAST")
            !otherNode.collisionContainsElement(it as E)
        }
        return when (totalWritten) {
            0 -> EMPTY
            1 -> tempBuffer[0]
            this.buffer.size -> this
            tempBuffer.size -> setProperties(newBitmap = 0, newBuffer = tempBuffer, owner)
            else -> setProperties(newBitmap = 0, newBuffer = tempBuffer.copyOf(newSize = totalWritten), owner)
        }
    }

    fun calculateSize(): Int {
        if (bitmap == 0) return buffer.size
        var result = 0
        for (e in buffer) {
            result += when (e) {
                is TrieNode<*> -> e.calculateSize()
                else -> 1
            }
        }
        return result
    }

    private fun elementsIdentityEquals(otherNode: TrieNode<E>): Boolean {
        if (this === otherNode) return true
        if (bitmap != otherNode.bitmap) return false
        for (i in 0 until buffer.size) {
            if (buffer[i] !== otherNode.buffer[i]) return false
        }
        return true
    }

    fun contains(elementHash: Int, element: E, shift: Int): Boolean {
        val cellPositionMask = 1 shl indexSegment(elementHash, shift)

        if (hasNoCellAt(cellPositionMask)) { // element is absent
            return false
        }

        val cellIndex = indexOfCellAt(cellPositionMask)
        if (buffer[cellIndex] is TrieNode<*>) { // element may be in node
            val targetNode = nodeAtIndex(cellIndex)
            if (shift == MAX_SHIFT) {
                return targetNode.collisionContainsElement(element)
            }
            return targetNode.contains(elementHash, element, shift + LOG_MAX_BRANCHING_FACTOR)
        }
        // element is directly in buffer
        return element == buffer[cellIndex]
    }

    fun mutableAddAll(otherNode: TrieNode<E>, shift: Int, owner: MutabilityOwnership): TrieNode<E> {
        if (this === otherNode) {
            return this
        }
        if (shift > MAX_SHIFT) {
            return mutableCollisionAddAll(otherNode, owner)
        }
        // union mask contains all the bits from input masks
        val newBitMap = bitmap or otherNode.bitmap
        // first allocate the node and then fill it in
        // we are doing a union, so all the array elements are guaranteed to exist
        val mutableNode = when {
            newBitMap == bitmap && ownedBy == owner -> this
            else -> TrieNode<E>(newBitMap, arrayOfNulls<Any?>(newBitMap.countOneBits()), owner)
        }
        // for each bit set in the resulting mask,
        // either left, right or both masks contain the same bit
        // Note: we shouldn't overrun MAX_SHIFT because both sides are correct TrieNodes, right?
        newBitMap.forEachOneBit { positionMask, newNodeIndex ->
            val thisIndex = indexOfCellAt(positionMask)
            val otherNodeIndex = otherNode.indexOfCellAt(positionMask)
            mutableNode.buffer[newNodeIndex] = when {
                // no element on left -> pick right
                hasNoCellAt(positionMask) -> otherNode.buffer[otherNodeIndex]
                // no element on right -> pick left
                otherNode.hasNoCellAt(positionMask) -> buffer[thisIndex]
                // both nodes contain something at the masked bit
                else -> {
                    val thisCell = buffer[thisIndex]
                    val otherNodeCell = otherNode.buffer[otherNodeIndex]
                    val thisIsNode = thisCell is TrieNode<*>
                    val otherIsNode = otherNodeCell is TrieNode<*>
                    when {
                        // both are nodes -> merge them recursively
                        thisIsNode && otherIsNode -> @Suppress("UNCHECKED_CAST") {
                            thisCell as TrieNode<E>
                            otherNodeCell as TrieNode<E>
                            thisCell.mutableAddAll(
                                    otherNodeCell,
                                    shift + LOG_MAX_BRANCHING_FACTOR,
                                    owner
                            )
                        }
                        // one of them is a node -> add the other one to it
                        thisIsNode -> @Suppress("UNCHECKED_CAST") {
                            thisCell as TrieNode<E>
                            otherNodeCell as E
                            thisCell.mutableAdd(
                                    otherNodeCell.hashCode(),
                                    otherNodeCell,
                                    shift + LOG_MAX_BRANCHING_FACTOR,
                                    owner
                            )
                        }
                        // same as last case, but reversed
                        otherIsNode -> @Suppress("UNCHECKED_CAST") {
                            otherNodeCell as TrieNode<E>
                            thisCell as E
                            otherNodeCell.mutableAdd(
                                    thisCell.hashCode(),
                                    thisCell,
                                    shift + LOG_MAX_BRANCHING_FACTOR,
                                    owner
                            )
                        }
                        // both are just E => compare them
                        thisCell == otherNodeCell -> thisCell
                        // both are just E, but different => make a collision-ish node
                        else -> @Suppress("UNCHECKED_CAST") {
                            thisCell as E
                            otherNodeCell as E
                            makeNode(
                                    thisCell.hashCode(),
                                    thisCell,
                                    otherNodeCell.hashCode(),
                                    otherNodeCell,
                                    shift + LOG_MAX_BRANCHING_FACTOR,
                                    owner
                            )
                        }
                    }
                }
            }
        }
        return when {
            this.elementsIdentityEquals(mutableNode) -> this
            otherNode.elementsIdentityEquals(mutableNode) -> otherNode
            else -> mutableNode
        }
    }

    fun mutableRetainAll(otherNode: TrieNode<E>, shift: Int, owner: MutabilityOwnership): Any? {
        if (this === otherNode) {
            return this
        }
        if (shift > MAX_SHIFT) {
            return mutableCollisionRetainAll(otherNode, owner)
        }
        // intersection mask contains bits that are set in both inputs
        // this mask is not final 'cos some children may have no intersection
        val newBitMap = bitmap and otherNode.bitmap
        // zero means no nodes intersect
        if (newBitMap == 0) return EMPTY
        val mutableNode =
                if (ownedBy == owner && newBitMap == bitmap) this
                else TrieNode<E>(newBitMap, arrayOfNulls<Any?>(newBitMap.countOneBits()), owner)
        // we need to keep track of the real mask 'cos some of the children may intersect to nothing
        var realBitMap = 0
        // for each bit in intersection mask, try to intersect children
        newBitMap.forEachOneBit { positionMask, newNodeIndex ->
            val thisIndex = indexOfCellAt(positionMask)
            val otherNodeIndex = otherNode.indexOfCellAt(positionMask)
            val newValue = run {
                val thisCell = buffer[thisIndex]
                val otherNodeCell = otherNode.buffer[otherNodeIndex]
                val thisIsNode = thisCell is TrieNode<*>
                val otherIsNode = otherNodeCell is TrieNode<*>
                when {
                    // both are nodes -> merge them recursively
                    thisIsNode && otherIsNode -> @Suppress("UNCHECKED_CAST") {
                        thisCell as TrieNode<E>
                        otherNodeCell as TrieNode<E>
                        thisCell.mutableRetainAll(
                                otherNodeCell,
                                shift + LOG_MAX_BRANCHING_FACTOR,
                                owner
                        )
                    }
                    // one of them is a node -> check containment
                    thisIsNode -> @Suppress("UNCHECKED_CAST") {
                        thisCell as TrieNode<E>
                        otherNodeCell as E
                        if (thisCell.contains(otherNodeCell.hashCode(), otherNodeCell, shift + LOG_MAX_BRANCHING_FACTOR)) {
                            otherNodeCell
                        } else EMPTY
                    }
                    // same as last case, but reversed
                    otherIsNode -> @Suppress("UNCHECKED_CAST") {
                        otherNodeCell as TrieNode<E>
                        thisCell as E
                        if (otherNodeCell.contains(thisCell.hashCode(), thisCell, shift + LOG_MAX_BRANCHING_FACTOR)) {
                            thisCell
                        } else EMPTY
                    }
                    // both are just E => compare them
                    thisCell == otherNodeCell -> thisCell
                    // both are just E, but different => return nothing
                    else -> EMPTY
                }
            }
            if (newValue !== EMPTY) {
                // elements that are not in realBitMap will be removed later
                realBitMap = realBitMap or positionMask
            }
            mutableNode.buffer[newNodeIndex] = newValue
        }
        // resulting array's size is the popcount of resulting mask
        val realSize = realBitMap.countOneBits()
        return when {
            realBitMap == 0 -> EMPTY
            realBitMap == newBitMap -> {
                when {
                    mutableNode.elementsIdentityEquals(this) -> this
                    mutableNode.elementsIdentityEquals(otherNode) -> otherNode
                    else -> mutableNode
                }
            }
            // single values are kept only on root level
            realSize == 1 && shift != 0 -> when (val single = mutableNode.buffer[mutableNode.indexOfCellAt(realBitMap)]) {
                is TrieNode<*> -> TrieNode<E>(realBitMap, arrayOf(single), owner)
                else -> single
            }
            else -> {
                // clean up all the EMPTYs in the resulting buffer
                val realBuffer = arrayOfNulls<Any>(realSize)
                mutableNode.buffer.filterTo(realBuffer)
                TrieNode<E>(realBitMap, realBuffer, owner)
            }
        }
    }

    private fun mutableRemoveAll(otherNode: TrieNode<E>, shift: Int, owner: MutabilityOwnership): Any? {
        if (this === otherNode) {
            return EMPTY
        }
        if (shift > MAX_SHIFT) {
            return mutableCollisionRemoveAll(otherNode, owner)
        }
        // same as with intersection, only children of both nodes are considered
        // this mask is not final 'cos some children may have no intersection
        val removalBitmap = bitmap and otherNode.bitmap
        // zero means no intersection => nothing to remove
        if (removalBitmap == 0) return this
        // node here is either us (if we are mutable) or a mutable copy
        val mutableNode =
                if (ownedBy == owner) this
                else TrieNode<E>(bitmap, buffer.copyOf(), owner)
        // keep track of the real mask
        var realBitMap = bitmap
        removalBitmap.forEachOneBit { positionMask, _ ->
            val thisIndex = indexOfCellAt(positionMask)
            val otherNodeIndex = otherNode.indexOfCellAt(positionMask)
            val newValue = run {
                val thisCell = buffer[thisIndex]
                val otherNodeCell = otherNode.buffer[otherNodeIndex]
                val thisIsNode = thisCell is TrieNode<*>
                val otherIsNode = otherNodeCell is TrieNode<*>
                when {
                    // both are nodes -> merge them recursively
                    thisIsNode && otherIsNode -> @Suppress("UNCHECKED_CAST") {
                        thisCell as TrieNode<E>
                        otherNodeCell as TrieNode<E>
                        thisCell.mutableRemoveAll(
                                otherNodeCell,
                                shift + LOG_MAX_BRANCHING_FACTOR,
                                owner
                        )
                    }
                    // one of them is a node -> remove single element
                    thisIsNode -> @Suppress("UNCHECKED_CAST") {
                        thisCell as TrieNode<E>
                        otherNodeCell as E
                        val (removed, hasChanged) = thisCell.mutableRemove(
                                otherNodeCell.hashCode(),
                                otherNodeCell,
                                shift + LOG_MAX_BRANCHING_FACTOR,
                                owner)

                        // additional check needed for removal
                        if (hasChanged) {
                            if (removed.buffer.size == 1 && removed.buffer[0] !is TrieNode<*>) removed.buffer[0]
                            else removed
                        } else thisCell
                    }
                    // same as last case, but reversed
                    otherIsNode -> @Suppress("UNCHECKED_CAST") {
                        otherNodeCell as TrieNode<E>
                        thisCell as E
                        // "removing" a node from a value is basically checking if the value is contained in the node
                        if (otherNodeCell.contains(thisCell.hashCode(), thisCell, shift + LOG_MAX_BRANCHING_FACTOR)) {
                            EMPTY
                        } else thisCell
                    }
                    // both are just E => compare them
                    thisCell == otherNodeCell -> {
                        EMPTY
                    }
                    // both are just E, but different => nothing to remove, return left
                    else -> thisCell
                }
            }
            if (newValue === EMPTY) {
                // if we removed something, keep track
                realBitMap = realBitMap xor positionMask
            }
            mutableNode.buffer[thisIndex] = newValue
        }
        // resulting size is popcount of the resulting mask
        val realSize = realBitMap.countOneBits()
        return when {
            realBitMap == 0 -> EMPTY
            realBitMap == bitmap -> {
                when {
                    mutableNode.elementsIdentityEquals(this) -> this
                    else -> mutableNode
                }
            }
            // single values are kept only on root level
            realSize == 1 && shift != 0 -> when (val single = mutableNode.buffer[mutableNode.indexOfCellAt(realBitMap)]) {
                is TrieNode<*> -> TrieNode<E>(realBitMap, arrayOf(single), owner)
                else -> single
            }
            else -> {
                // clean up all the EMPTYs in the resulting buffer
                val realBuffer = arrayOfNulls<Any>(realSize)
                mutableNode.buffer.filterTo(realBuffer)
                TrieNode<E>(realBitMap, realBuffer, owner)
            }
        }
    }

    private fun containsAll(otherNode: TrieNode<E>, shift: Int): Boolean {
        if (this === otherNode) return true
        // essentially `buffer.containsAll(otherNode.buffer)`
        if (shift > MAX_SHIFT) return otherNode.buffer.all { it in buffer }

        // potential bitmap is an intersection of input bitmaps
        val potentialBitMap = bitmap and otherNode.bitmap
        // left bitmap must contain right bitmap => right bitmap must be equal to intersection
        if (potentialBitMap != otherNode.bitmap) return false
        // check each child, shortcut to false if any one isn't contained
        potentialBitMap.forEachOneBit { positionMask, _ ->
            val thisIndex = indexOfCellAt(positionMask)
            val otherNodeIndex = otherNode.indexOfCellAt(positionMask)
            val thisCell = buffer[thisIndex]
            val otherNodeCell = otherNode.buffer[otherNodeIndex]
            val thisIsNode = thisCell is TrieNode<*>
            val otherIsNode = otherNodeCell is TrieNode<*>
            when {
                // both are nodes => check recursively
                thisIsNode && otherIsNode -> @Suppress("UNCHECKED_CAST") {
                    thisCell as TrieNode<E>
                    otherNodeCell as TrieNode<E>
                    thisCell.containsAll(otherNodeCell, shift + LOG_MAX_BRANCHING_FACTOR) || return false
                }
                // left is node, right is just E => check containment
                thisIsNode -> @Suppress("UNCHECKED_CAST") {
                    thisCell as TrieNode<E>
                    otherNodeCell as E
                    thisCell.contains(otherNodeCell.hashCode(), otherNodeCell, shift + LOG_MAX_BRANCHING_FACTOR) || return false
                }
                // left is just E, right is node => not possible
                otherIsNode -> return false
                // both are just E => containment is just equality
                else -> thisCell == otherNodeCell || return false
            }
        }
        return true
    }

    fun add(elementHash: Int, element: E, shift: Int): TrieNode<E> {
        val cellPositionMask = 1 shl indexSegment(
            elementHash,
            shift
        )

        if (hasNoCellAt(cellPositionMask)) { // element is absent
            return addElementAt(cellPositionMask, element, owner = null)
        }

        val cellIndex = indexOfCellAt(cellPositionMask)
        if (buffer[cellIndex] is TrieNode<*>) { // element may be in node
            val targetNode = nodeAtIndex(cellIndex)
            val newNode = if (shift == MAX_SHIFT) {
                targetNode.collisionAdd(element)
            } else {
                targetNode.add(elementHash, element, shift + LOG_MAX_BRANCHING_FACTOR)
            }
            if (targetNode === newNode) return this
            return setCellAtIndex(cellIndex, newNode, owner = null)
        }
        // element is directly in buffer
        if (element == buffer[cellIndex]) return this
        return moveElementToNode(cellIndex, elementHash, element, shift, owner = null)
    }

    private fun mutableAdd(elementHash: Int, element: E, shift: Int, owner: MutabilityOwnership): TrieNode<E> {
        val cellPosition = 1 shl indexSegment(elementHash, shift)

        if (hasNoCellAt(cellPosition)) { // element is absent
            return addElementAt(cellPosition, element, owner)
        }

        val cellIndex = indexOfCellAt(cellPosition)
        if (buffer[cellIndex] is TrieNode<*>) { // element may be in node
            val targetNode = nodeAtIndex(cellIndex)
            val newNode = if (shift == MAX_SHIFT) {
                targetNode.mutableCollisionAdd(element, owner)
            } else {
                targetNode.mutableAdd(elementHash, element, shift + LOG_MAX_BRANCHING_FACTOR, owner)
            }
            if (targetNode === newNode) return this
            return setCellAtIndex(cellIndex, newNode, owner)
        }
        // element is directly in buffer
        if (element == buffer[cellIndex]) return this
        return moveElementToNode(cellIndex, elementHash, element, shift, owner)
    }

    fun remove(elementHash: Int, element: E, shift: Int): TrieNode<E> {
        val cellPositionMask = 1 shl indexSegment(
            elementHash,
            shift
        )

        if (hasNoCellAt(cellPositionMask)) { // element is absent
            return this
        }

        val cellIndex = indexOfCellAt(cellPositionMask)
        if (buffer[cellIndex] is TrieNode<*>) { // element may be in node
            val targetNode = nodeAtIndex(cellIndex)
            val newNode = if (shift == MAX_SHIFT) {
                targetNode.collisionRemove(element)
            } else {
                targetNode.remove(elementHash, element, shift + LOG_MAX_BRANCHING_FACTOR)
            }
            if (targetNode === newNode) return this
            return canonicalizeNodeAtIndex(cellIndex, newNode, owner = null)
        }
        // element is directly in buffer
        if (element == buffer[cellIndex]) {
            return removeCellAtIndex(cellIndex, cellPositionMask, owner = null)
        }
        return this
    }

    private fun mutableRemove(
        elementHash: Int,
        element: E,
        shift: Int,
        owner: MutabilityOwnership
    ): Pair<TrieNode<E>, Boolean> {
        val cellPositionMask = 1 shl indexSegment(elementHash, shift)

        if (hasNoCellAt(cellPositionMask)) { // element is absent
            return this to false
        }

        val cellIndex = indexOfCellAt(cellPositionMask)
        if (buffer[cellIndex] is TrieNode<*>) { // element may be in node
            val targetNode = nodeAtIndex(cellIndex)
            val (newNode, hasChanged) = if (shift == MAX_SHIFT) {
                targetNode.mutableCollisionRemove(element, owner)
            } else {
                targetNode.mutableRemove(elementHash, element, shift + LOG_MAX_BRANCHING_FACTOR, owner)
            }
            // If newNode is a single-element node, it is newly created, or targetNode is owned by mutator and a cell was removed in-place.
            //      Otherwise the single element would have been lifted up.
            // If targetNode is owned by mutator, this node is also owned by mutator. Thus no new node will be created to replace this node.
            // If newNode !== targetNode, it is newly created.
            if (targetNode.ownedBy !== owner && targetNode === newNode) return this to hasChanged
            return canonicalizeNodeAtIndex(cellIndex, newNode, owner) to hasChanged
        }
        // element is directly in buffer
        if (element == buffer[cellIndex]) {
            return removeCellAtIndex(cellIndex, cellPositionMask, owner) to true   // check is empty
        }
        return this to false
    }

    fun remove(element: E, owner: MutabilityOwnership): TrieNode<E> =
        mutableRemove(element.hashCode(), element, 0, owner).first

    @Suppress("UNCHECKED_CAST")
    fun removeAll(otherNode: TrieNode<E>, owner: MutabilityOwnership): TrieNode<E> =
        mutableRemoveAll(otherNode, 0, owner) as TrieNode<E>

    fun add(element: E, owner: MutabilityOwnership): TrieNode<E> =
        mutableAdd(element.hashCode(), element, 0, owner)

    fun addAll(otherNode: TrieNode<E>, owner: MutabilityOwnership): TrieNode<E> =
        mutableAddAll(otherNode, 0, owner)

    fun contains(element: E): Boolean = contains(element.hashCode(), element, 0)

    fun containsAll(otherNode: TrieNode<E>): Boolean = containsAll(otherNode, 0)

    @Suppress("UNCHECKED_CAST")
    fun retainAll(otherNode: TrieNode<E>, owner: MutabilityOwnership) =
        mutableRetainAll(otherNode, 0, owner) as TrieNode<E>

    @Suppress("UNCHECKED_CAST")
    fun clear() = EMPTY as TrieNode<E>

    override fun toString(): String =
        iterator().asSequence().joinToString(separator = ", ", prefix = "{", postfix = "}") { it.toString() }

    override fun hashCode(): Int = sumOf { it.hashCode() }

    override fun equals(other: Any?): Boolean {
        @Suppress("UNCHECKED_CAST")
        other as? TrieNode<E> ?: return false
        return other.calculateSize() == this.calculateSize() && other.containsAll(this)
    }

    override fun iterator(): Iterator<E> = UPersistentHashSetIterator(this)

    internal companion object {
        internal val EMPTY = TrieNode<Nothing>(0, emptyArray())
    }
}
