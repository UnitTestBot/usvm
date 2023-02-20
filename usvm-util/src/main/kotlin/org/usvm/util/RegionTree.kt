package org.usvm.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import java.util.NoSuchElementException

/**
 * Region tree is a data structure storing collection of keys by abstract regions.
 * It maintains the following two invariants:
 * * (1) all sibling regions are pairwise disjoint;
 * * (2) all child regions are included into parent region.
 */
class RegionTree<Key, Reg>(
    val entries: PersistentMap<Reg, Pair<Key, RegionTree<Key, Reg>>>
) : Iterable<Pair<Key, Reg>> where Reg : Region<Reg> {
    @Suppress("MemberVisibilityCanBePrivate")
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Splits the region tree into two trees: completely covered by the [region] and disjoint with it.
     *
     * [keyFilter] is an arbitrary predicate suitable to filter out nodes from the results of the function.
     * Examples:
     * * `{ it != 10 }` removes all the nodes with `key` equal to 10
     * * `{ false }` doesn't remove anything
     */
    private fun splitRecursively(
        region: Reg,
        keyFilter: (Key) -> Boolean
    ): RecursiveSplitResult {
        if (isEmpty) {
            return RecursiveSplitResult(completelyCoveredRegionTree = this, disjointRegionTree = this)
        }

        val entry = entries[region]

        // If we have precisely such region in the tree, then all its siblings are disjoint by invariant (1).
        // So just return a `Pair(node storing the region, rest of its siblings)`
        if (entry != null) {
            val key = entry.first
            // IMPORTANT: usage of linked versions of maps is mandatory here, since
            // it is required for correct order of keys returned by `iterator.next()` method
            val inside = if (keyFilter(key)) persistentMapOf() else persistentMapOf(region to entry)
            val outside = entries.remove(region)

            val completelyCoveredRegionTree = RegionTree(inside)
            val disjointRegionTree = RegionTree(outside)

            return RecursiveSplitResult(completelyCoveredRegionTree, disjointRegionTree)
        }

        // Such region doesn't exist. Do it slow way: iterate all the entries, group them into:
        // (1) included by the [region],
        // (2) disjoint with the [region],
        // (3) partially intersected with the [region].

        // IMPORTANT: usage of linked versions of maps is mandatory here, since
        // it is required for correct order of keys returned by `iterator.next()` method
        // We have to support the following order: assume that we had entries [e0, e1, e2, e3, e4]
        // and a write operation into a region R that is a subregion of `e1` and `e3`, and covers e2 completely.
        // The correct order of the result is:\
        // included = [R ∩ e1, e2, R ∩ e3], disjoint = [e0, e1\R, e3\R, e4]
        // That allows us to move recently updates region to the right side of the `entries` map and
        // leave the oldest ones in the left part of it.
        val included = mutableMapOf<Reg, Pair<Key, RegionTree<Key, Reg>>>()
        val disjoint = mutableMapOf<Reg, Pair<Key, RegionTree<Key, Reg>>>()

        entries.entries.forEach { (nodeRegion, keyWithRegionTree) ->
            if (keyFilter(keyWithRegionTree.first)) {
                // If we want to filter a region associated with the [key],
                // we can simply do not add it to the `included` map result
                return@forEach
            } else {
                when (region.compare(nodeRegion)) {
                    RegionComparisonResult.INCLUDES -> included += nodeRegion to keyWithRegionTree
                    RegionComparisonResult.DISJOINT -> disjoint += nodeRegion to keyWithRegionTree
                    // For nodes with intersection, repeat process recursively.
                    RegionComparisonResult.INTERSECTS -> {
                        val (key, childRegionTree) = keyWithRegionTree
                        val (splitIncluded, splitDisjoint) = childRegionTree.splitRecursively(region, keyFilter)

                        val includedReg = nodeRegion.intersect(region)
                        val disjointReg = nodeRegion.subtract(region)

                        included[includedReg] = key to splitIncluded
                        disjoint[disjointReg] = key to splitDisjoint
                    }
                }
            }
        }

        // IMPORTANT: usage of linked versions of maps is mandatory here, since
        // it is required for correct order of keys returned by `iterator.next()` method
        val includedRegionTree = CompletelyCoveredRegionTree(included.toPersistentMap())
        val disjointRegionTree = DisjointRegionTree(disjoint.toPersistentMap())

        return RecursiveSplitResult(includedRegionTree, disjointRegionTree)
    }

    /**
     * Returns a subtree completely included into the [region].
     */
    fun localize(region: Reg): RegionTree<Key, Reg> =
        splitRecursively(region, keyFilter = { false }).completelyCoveredRegionTree

    /**
     * Places a Pair([region], [key]) into the tree, preserving its invariants.
     *
     * [keyFilter] is a predicate suitable to filter out particular nodes if their `key` satisfies it.
     * Examples:
     * * `{ false }` doesn't filter anything
     * * `{ it != key }` writes into a tree and restrict for it to contain non-unique keys.
     *   Suitable for deduplication.
     */
    fun write(region: Reg, key: Key, keyFilter: (Key) -> Boolean = { false }): RegionTree<Key, Reg> {
        val (included, disjoint) = splitRecursively(region, keyFilter)
        // A new node for a tree we construct accordingly to the (2) invariant.
        val value = key to included

        // Construct entries accordingly to the (1) invariant.
        val disjointEntries = disjoint.entries.put(region, value)

        return RegionTree(disjointEntries)
    }

    // TODO: add collection operations, like map, fold, filter, flatten, etc...

    private fun checkInvariantRecursively(parentRegion: Reg?): Boolean {
        // Invariant (2): all child regions are included into parent region
        val secondInvariant = parentRegion == null || entries.entries.all { (key, _) ->
            parentRegion.compare(key) == RegionComparisonResult.INCLUDES
        }

        val checkInvariantRecursively = {
            entries.entries.all { (entryKey, value) ->
                // Invariant (1): all sibling regions are pairwise disjoint
                val firstInvariant = entries.entries.all { other ->
                    val otherKey = other.key
                    otherKey === entryKey || entryKey.compare(otherKey) == RegionComparisonResult.DISJOINT
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
    override fun iterator(): Iterator<Pair<Key, Reg>> = TheLeftestTopSortIterator(entries.iterator())

    override fun toString(): String = if (isEmpty) "emptyTree" else toString(balance = 0)

    private fun toString(balance: Int): String =
        entries.entries.joinToString(separator = System.lineSeparator()) {
            val subtree = it.value.second
            val region = it.key
            val key = it.value.first
            val indent = "\t".repeat(balance)

            val subtreeString = if (subtree.isEmpty) {
                "\t" + indent + "emptyTree"
            } else {
                subtree.toString(balance + 1)
            }

            indent + "$region -> $key:${System.lineSeparator()}$subtreeString"
        }

    /**
     * [entriesIterators] should be considered as a recursion stack where
     * the last element is the deepest one in the branch we explore.
     */
    private inner class TheLeftestTopSortIterator private constructor(
        private val entriesIterators: MutableList<RegionTreeEntryIterator<Key, Reg>>,
    ) : Iterator<Pair<Key, Reg>> {
        // A stack of elements we should emit after we process all their children.
        // We cannot use for it corresponding iterators since every value from an
        // iterator can be retrieved only once, but we already got it when we discovered the previous layer.
        private val nodes: MutableList<RegionTreeMapEntry<Key, Reg>> = mutableListOf()

        constructor(iterator: RegionTreeEntryIterator<Key, Reg>) : this(mutableListOf(iterator))

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

        override fun next(): Pair<Key, Reg> {
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
                val keyWithRegionTree = entry.value
                val regionTree = keyWithRegionTree.second

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
        val completelyCoveredRegionTree: CompletelyCoveredRegionTree<Key, Reg>,
        val disjointRegionTree: DisjointRegionTree<Key, Reg>
    ) {
        operator fun component1(): RegionTree<Key, Reg> = completelyCoveredRegionTree
        operator fun component2(): RegionTree<Key, Reg> = disjointRegionTree
    }
}

private typealias DisjointRegionTree<Key, Reg> = RegionTree<Key, Reg>
private typealias CompletelyCoveredRegionTree<Key, Reg> = RegionTree<Key, Reg>
private typealias RegionTreeMapEntry<Key, Reg> = Map.Entry<Reg, Pair<Key, RegionTree<Key, Reg>>>
private typealias RegionTreeEntryIterator<Key, Reg> = Iterator<RegionTreeMapEntry<Key, Reg>>

fun <Key, Reg : Region<Reg>> emptyRegionTree() = RegionTree<Key, Reg>(persistentMapOf())
