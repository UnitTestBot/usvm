/*
 * Copyright 2016-2019 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package org.usvm.collections.immutable.implementations.immutableMap

import kotlinx.collections.immutable.ImmutableSet

internal class UPersistentHashMapEntries<K, V>(
    private val trie: TrieNode<K, V>
) : ImmutableSet<Map.Entry<K, V>>, AbstractSet<Map.Entry<K, V>>() {
    override val size: Int get() = throw UnsupportedOperationException()

    override fun iterator(): Iterator<Map.Entry<K, V>> {
        return UPersistentHashMapEntriesIterator(trie)
    }
}
