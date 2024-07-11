/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableMap

import org.usvm.collections.immutable.internal.MutabilityOwnership



internal class TrieMutableEntries<K, V>(private val node: TrieNode<K,V>, val ownership: MutabilityOwnership) : Iterable<MutableMap.MutableEntry<K,V>>{
    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        return TrieMutableIterator(node, ownership)
    }
}


