/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableSet

import org.usvm.collections.immutable.internal.MutabilityOwnership

internal class UPersistentHashSetMutableIterator<E>(private val node: TrieNode<E>, val owner: MutabilityOwnership)
    : UPersistentHashSetIterator<E>(node), MutableIterator<E> {
    private var lastIteratedElement: E? = null
    private var nextWasInvoked = false

    override fun next(): E {
        val next = super.next()
        lastIteratedElement = next
        nextWasInvoked = true
        return next
    }

    override fun remove() {
        checkNextWasInvoked()
        if (hasNext()) {
            val currentElement = currentElement()

            node.remove(lastIteratedElement!!, owner)
            resetPath(currentElement.hashCode(), node, currentElement, 0)
        } else {
            node.remove(lastIteratedElement!!, owner)
        }

        lastIteratedElement = null
        nextWasInvoked = false
    }

    private fun resetPath(hashCode: Int, node: TrieNode<*>, element: E, pathIndex: Int) {
        if (isCollision(node)) {
            val index = node.buffer.indexOf(element)
            assert(index != -1)
            path[pathIndex].reset(node.buffer, index)
            pathLastIndex = pathIndex
            return
        }

        val position = 1 shl indexSegment(hashCode, pathIndex * LOG_MAX_BRANCHING_FACTOR)
        val index = node.indexOfCellAt(position)

        path[pathIndex].reset(node.buffer, index)

        val cell = node.buffer[index]
        if (cell is TrieNode<*>) {
            resetPath(hashCode, cell, element, pathIndex + 1)
        } else {
//            assert(cell == element)
            pathLastIndex = pathIndex
        }
    }

    private fun isCollision(node: TrieNode<*>): Boolean {
        return node.bitmap == 0
    }

    private fun checkNextWasInvoked() {
        if (!nextWasInvoked)
            throw IllegalStateException()
    }
}
