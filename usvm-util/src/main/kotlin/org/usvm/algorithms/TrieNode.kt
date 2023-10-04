package org.usvm.algorithms

class TrieNode<E, V> private constructor(
    private val depth: Int,
    private var parent: TrieNode<E, V>?,
    private var parentEdge: E?,
    var value: V
) {
    private val _children = mutableMapOf<E, TrieNode<E, V>>()
    val children: Map<E, TrieNode<E, V>> = _children

    fun add(edge: E, defaultValue: () -> V): TrieNode<E, V> =
        _children.getOrPut(edge) {
            TrieNode(depth + 1, parent = this, parentEdge = edge, defaultValue())
        }

    fun remove(edge: E): TrieNode<E, V>? = _children.remove(edge)
    fun drop(): TrieNode<E, V>? {
        val parent = parent
        if (parent != null) {
            parent.remove(parentEdge!!)
            this.parent = null
            this.parentEdge = null
            return parent
        }
        return null
    }

    fun merge(other: TrieNode<E, V>, pathMerger: (List<V>, List<V>) -> Pair<E, V>): TrieNode<E, V> {
        val (lca, thisSuffix, otherSuffix) = findLcaLinear(
            this,
            other,
            { requireNotNull(it.parent) },
            { it.depth },
            { it.value }
        )
        val (mergedEdge, mergedValue) = pathMerger(thisSuffix, otherSuffix)
        return lca.add(mergedEdge) { mergedValue }
    }

    companion object {
        fun <E, V> root(defaultValue: () -> V): TrieNode<E, V> {
            return TrieNode(0, parent = null, parentEdge = null, defaultValue())
        }
    }
}
