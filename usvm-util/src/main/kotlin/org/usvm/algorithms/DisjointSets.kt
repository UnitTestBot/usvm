package org.usvm.algorithms

/**
 * Mutable union-find data structure. Represents a collection of disjoint sets of elements of type [T].
 * Initially, every set is a singleton element.
 * Has two operations: [union] (x, y), which computes union of two sets containing x and y,
 * and [find] (x), which finds a representative of set containing x.
 * All actual set changes in this data structures can be listened by [subscribe].
 */
class DisjointSets<T> private constructor(
    private val parent: MutableMap<T, T>,
    private val rank: MutableMap<T, Int>,
    private var unionCallback: ((T, T) -> Unit)?,
    private val representativeSelector: RepresentativeSelector<T>?,
) : Iterable<Map.Entry<T, T>> by parent.entries {

    /**
     * Create a new empty disjoint sets data structure.
     */
    constructor(representativeSelector: RepresentativeSelector<T>? = null) :
        this(mutableMapOf(), mutableMapOf(), unionCallback = null, representativeSelector)

    /**
     * Returns representative of set containing [x].
     * Might change the internal representation in order to optimise things up.
     */
    fun find(x: T): T {
        val p = parent[x] ?: return x
        val root = find(p)
        parent[x] = root
        return root
    }

    /**
     * Returns if [x] and [y] are contained in the same set.
     */
    fun connected(x: T, y: T) = find(x) == find(y)

    private fun merge(x: T, y: T) {
        parent[y] = x
        unionCallback?.let { it(x, y) }
    }

    /**
     * Merges two sets containing [x] and [y].
     * If [x] and [y] are already in the same set, does nothing.
     * Otherwise, calls all the callbacks subscribed to this instance.
     */
    fun union(x: T, y: T) {
        val u = find(x)
        val v = find(y)

        if (u == v) {
            return
        }

        representativeSelector?.let {
            if (it.shouldSelectAsRepresentative(u)) {
                merge(u, v)

                return
            }

            if (it.shouldSelectAsRepresentative(v)) {
                merge(v, u)

                return
            }
        }

        val rankU = rank[u] ?: 0
        val rankV = rank[v] ?: 0

        when {
            rankU > rankV -> merge(u, v)
            rankU < rankV -> merge(v, u)
            else -> {
                merge(u, v)
                rank[u] = rankU + 1
            }
        }
    }

    /**
     * Subscribes [callback] to modifications of this data structure.
     * [callback](x, y) notifies that two sets with representatives x and y
     * have been merged into one set with representative x (i.e., the order of arguments matters!)
     */
    fun subscribe(callback: (T, T) -> Unit) {
        unionCallback = unionCallback?.let { { x, y -> it(x, y); callback(x, y) } } ?: callback
    }

    /**
     * Resets this structure to default state, where every set is a singleton element.
     */
    fun clear() {
        parent.clear()
        rank.clear()
        unionCallback = null
    }


    /**
     * Creates a copy of this structure.
     * Note that current subscribers get unsubscribed!
     */
    fun clone() = DisjointSets(parent.toMutableMap(), rank.toMutableMap(), unionCallback = null, representativeSelector)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DisjointSets<*>

        if (parent != other.parent) return false
        if (rank != other.rank) return false

        return true
    }

    override fun hashCode(): Int {
        var result = parent.hashCode()
        result = 31 * result + rank.hashCode()
        return result
    }

    /**
     * Determines what values should be selected as representative during merging.
     */
    fun interface RepresentativeSelector<T> {
        fun shouldSelectAsRepresentative(value: T): Boolean
    }
}
