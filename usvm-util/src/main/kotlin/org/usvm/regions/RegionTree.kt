package org.usvm.regions

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 * Region tree is a data structure storing collection of values by abstract regions. You can think, that
 * [RegionTree] is actually a persistent event-storage (event == [Value]) with
 * support of grouped reading and writing, where events live in specific regions.
 *
 * It maintains the following two invariants:
 * * (1) all sibling regions are pairwise disjoint;
 * * (2) all child regions are included into parent region.
 *
 * @param Reg a region of writing or reading
 * @param Value a value to write or read
 */
class RegionTree<Reg, Value>(
    val entries: PersistentMap<Reg, Pair<Value, RegionTree<Reg, Value>>>,
) : Iterable<Pair<Value, Reg>> where Reg : Region<Reg> {
    @Suppress("MemberVisibilityCanBePrivate")
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Splits the region tree into two trees: completely covered by the [region] and disjoint with it.
     *
     * [filterPredicate] is a predicate suitable to filter out particular nodes if their `value` don't satisfy it.
     * Examples:
     * * `{ true }` doesn't filter anything
     * * `{ it != value }` writes into a tree and restrict for it to contain non-unique values.
     * @see localize
     */
    private fun splitRecursively(
        region: Reg,
        filterPredicate: (Value) -> Boolean,
    ): RecursiveSplitResult {
        if (isEmpty) {
            return RecursiveSplitResult(completelyCoveredRegionTree = this, disjointRegionTree = this)
        }

        val entry = entries[region]

        // If we have precisely such region in the tree, then all its siblings are disjoint by invariant (1).
        // So just return a `Pair(node storing the region, rest of its siblings)`
        if (entry != null) {
            val value = entry.first
            // IMPORTANT: usage of linked versions of maps is mandatory here, since
            // it is required for correct order of values returned by `iterator.next()` method
            val completelyCoveredRegionTree = if (filterPredicate(value)) {
                val inside = persistentMapOf(region to entry)
                if (entries.size == 1) {
                    this
                } else {
                    RegionTree(inside)
                }
            } else {
                // fixme: here we might have a deep recursion, maybe we should rewrite it
                entry.second.splitRecursively(region, filterPredicate).completelyCoveredRegionTree
            }
            val outside = entries.remove(region)
            val disjointRegionTree = RegionTree(outside)

            return RecursiveSplitResult(completelyCoveredRegionTree, disjointRegionTree)
        }

        // Such region doesn't exist. Do it slow way: iterate all the entries, group them into:
        // (1) included by the [region],
        // (2) disjoint with the [region],
        // (3) partially intersected with the [region].

        // IMPORTANT: usage of linked versions of maps is mandatory here, since
        // it is required for correct order of values returned by `iterator.next()` method
        // We have to support the following order: assume that we had entries [e0, e1, e2, e3, e4]
        // and a write operation into a region R that is a subregion of `e1` and `e3`, and covers e2 completely.
        // The correct order of the result is:
        // included = [R ∩ e1, e2, R ∩ e3], disjoint = [e0, e1\R, e3\R, e4]
        // That allows us to move recently updates region to the right side of the `entries` map and
        // leave the oldest ones in the left part of it.
        val included = mutableMapOf<Reg, Pair<Value, RegionTree<Reg, Value>>>()
        val disjoint = mutableMapOf<Reg, Pair<Value, RegionTree<Reg, Value>>>()

        fun MutableMap<Reg, Pair<Value, RegionTree<Reg, Value>>>.addWithFilter(
            nodeRegion: Reg,
            valueWithRegionTree: Pair<Value, RegionTree<Reg, Value>>,
            filterPredicate: (Value) -> Boolean,
        ) {
            val (value, childRegionTree) = valueWithRegionTree
            if (filterPredicate(value)) {
                put(nodeRegion, valueWithRegionTree)
            } else {
                putAll(childRegionTree.entries)
            }
        }

        entries.entries.forEach { (nodeRegion, valueWithRegionTree) ->
            when (region.compare(nodeRegion)) {
                Region.ComparisonResult.INCLUDES -> included.addWithFilter(nodeRegion, valueWithRegionTree, filterPredicate)

                Region.ComparisonResult.DISJOINT -> disjoint.addWithFilter(nodeRegion, valueWithRegionTree, filterPredicate)
                // For nodes with intersection, repeat process recursively.
                Region.ComparisonResult.INTERSECTS -> {
                    val (value, childRegionTree) = valueWithRegionTree
                    val (splitIncluded, splitDisjoint) = childRegionTree.splitRecursively(region, filterPredicate)

                    val includedReg = nodeRegion.intersect(region)
                    val disjointReg = nodeRegion.subtract(region)

                    included.addWithFilter(includedReg, value to splitIncluded, filterPredicate)
                    disjoint.addWithFilter(disjointReg, value to splitDisjoint, filterPredicate)
                }
            }
        }

        // IMPORTANT: usage of linked versions of maps is mandatory here, since
        // it is required for correct order of values returned by `iterator.next()` method
        val includedRegionTree = CompletelyCoveredRegionTree(included.toPersistentMap())
        val disjointRegionTree = DisjointRegionTree(disjoint.toPersistentMap())

        return RecursiveSplitResult(includedRegionTree, disjointRegionTree)
    }

    /**
     * Returns a subtree completely included into the [region].
     *
     * [filterPredicate] is a predicate suitable to filter out particular nodes if their `value` don't satisfy it.
     * Examples:
     * * `{ true }` doesn't filter anything
     * * `{ it != value }` writes into a tree and restrict for it to contain non-unique values.
     *   Suitable for deduplication.
     *
     * r := some region
     * tree := {r -> 1}
     *             {r -> 2}
     *                 {r -> 3}
     * tree.localize(r) { it % 2 == 1) =
     *     // first will be filtered out
     *         {r -> 2}
     *            // third will be filtered out
     *
     * ```
     */
    fun localize(region: Reg, filterPredicate: (Value) -> Boolean = { true }): RegionTree<Reg, Value> =
        splitRecursively(region, filterPredicate).completelyCoveredRegionTree

    /**
     * Returns a subtree completely included into the [region] and disjoint with it.
     */
    fun split(
        region: Reg,
        filterPredicate: (Value) -> Boolean = { true },
    ): Pair<CompletelyCoveredRegionTree<Reg, Value>, DisjointRegionTree<Reg, Value>> {
        val splitResult = splitRecursively(region, filterPredicate)
        return splitResult.completelyCoveredRegionTree to splitResult.disjointRegionTree
    }

    /**
     * Places a Pair([region], [value]) into the tree, preserving its invariants.
     *
     * [filterPredicate] is a predicate suitable to filter out particular nodes if their `value` don't satisfy it.
     * Examples:
     * * `{ true }` doesn't filter anything
     * * `{ it != value }` writes into a tree and restrict for it to contain non-unique values.
     *   Suitable for deduplication.
     * @see localize
     */
    fun write(region: Reg, value: Value, filterPredicate: (Value) -> Boolean = { true }): RegionTree<Reg, Value> {
        val (included, disjoint) = splitRecursively(region, filterPredicate)
        // A new node for a tree we construct accordingly to the (2) invariant.
        val node = value to included

        // Construct entries accordingly to the (1) invariant.
        val disjointEntries = disjoint.entries.put(region, node)

        return RegionTree(disjointEntries)
    }

    private fun checkInvariantRecursively(parentRegion: Reg?): Boolean {
        // Invariant (2): all child regions are included into parent region
        val secondInvariant = parentRegion == null || entries.entries.all { (reg, _) ->
            parentRegion.compare(reg) == Region.ComparisonResult.INCLUDES
        }

        val checkInvariantRecursively = {
            entries.entries.all { (entryKey, value) ->
                // Invariant (1): all sibling regions are pairwise disjoint
                val firstInvariant = entries.entries.all { other ->
                    val otherReg = other.key
                    otherReg === entryKey || entryKey.compare(otherReg) == Region.ComparisonResult.DISJOINT
                }

                firstInvariant && value.second.checkInvariantRecursively(entryKey)
            }
        }

        return secondInvariant && checkInvariantRecursively()
    }

    fun checkInvariant() {
        if (!checkInvariantRecursively(parentRegion = null)) {
            error("The invariant of region tree is violated!")
        }
    }

    /**
     * Returns an iterator that returns topologically sorted elements.
     * Note that elements from the same level will be processed in order from the
     * oldest entry to the most recently updated one.
     */
    override fun iterator(): Iterator<Pair<Value, Reg>> = TheLeftestTopSortIterator(entries.iterator())

    override fun toString(): String = if (isEmpty) "emptyTree" else toString(balance = 0)

    private fun toString(balance: Int): String =
        entries.entries.joinToString(separator = System.lineSeparator()) {
            val subtree = it.value.second
            val region = it.key
            val value = it.value.first
            val indent = "\t".repeat(balance)

            val subtreeString = if (subtree.isEmpty) {
                "\t" + indent + "emptyTree"
            } else {
                subtree.toString(balance + 1)
            }

            indent + "$region -> $value:${System.lineSeparator()}$subtreeString"
        }

    /**
     * [entriesIterators] should be considered as a recursion stack where
     * the last element is the deepest one in the branch we explore.
     */
    private inner class TheLeftestTopSortIterator private constructor(
        private val entriesIterators: MutableList<RegionTreeEntryIterator<Value, Reg>>,
    ) : Iterator<Pair<Value, Reg>> {
        // A stack of elements we should emit after we process all their children.
        // We cannot use for it corresponding iterators since every value from an
        // iterator can be retrieved only once, but we already got it when we discovered the previous layer.
        private val nodes: MutableList<RegionTreeMapEntry<Reg, Value>> = mutableListOf()

        constructor(iterator: RegionTreeEntryIterator<Value, Reg>) : this(mutableListOf(iterator))

        override fun hasNext(): Boolean {
            while (entriesIterators.isNotEmpty()) {
                val currentIterator = entriesIterators.last()

                if (!currentIterator.hasNext()) {
                    // We have nodes in the processing stack that we didn't emit yet
                    return nodes.isNotEmpty()
                }

                // We have elements to process inside the currentIterator
                return true
            }

            // Both iterators and nodes stacks are empty.
            return false
        }

        override fun next(): Pair<Value, Reg> {
            while (entriesIterators.isNotEmpty()) {
                val currentIterator = entriesIterators.last()

                // We reached an end of the current layer in the tree, go back to the previous one
                if (!currentIterator.hasNext()) {
                    entriesIterators.removeLast()
                    // We processed all the children, now we can emit their parent
                    return nodes.removeLast().let { it.value.first to it.key }
                }

                // Take the next element on the current layer
                val entry = currentIterator.next()
                val valueWithRegionTree = entry.value
                val regionTree = valueWithRegionTree.second

                // If it is a leaf, it is the answer, return it
                if (regionTree.isEmpty) {
                    return entry.value.first to entry.key
                }

                // Otherwise, add it in nodes list and an iterator for its children in the stack
                nodes += entry
                entriesIterators += regionTree.entries.iterator()
            }

            // That means that there are no unprocessed nodes in the tree
            throw NoSuchElementException()
        }
    }

    private inner class RecursiveSplitResult(
        val completelyCoveredRegionTree: CompletelyCoveredRegionTree<Reg, Value>,
        val disjointRegionTree: DisjointRegionTree<Reg, Value>,
    ) {
        operator fun component1(): RegionTree<Reg, Value> = completelyCoveredRegionTree
        operator fun component2(): RegionTree<Reg, Value> = disjointRegionTree
    }
}

private typealias DisjointRegionTree<Reg, Value> = RegionTree<Reg, Value>
private typealias CompletelyCoveredRegionTree<Reg, Value> = RegionTree<Reg, Value>
private typealias RegionTreeMapEntry<Reg, Value> = Map.Entry<Reg, Pair<Value, RegionTree<Reg, Value>>>
private typealias RegionTreeEntryIterator<Reg, Value> = Iterator<RegionTreeMapEntry<Value, Reg>>

private val EMPTY_REGION = RegionTree<Nothing, Nothing>(persistentMapOf())

@Suppress("UNCHECKED_CAST")
fun <Reg : Region<Reg>, Value> emptyRegionTree(): RegionTree<Reg, Value> = EMPTY_REGION as RegionTree<Reg, Value>
