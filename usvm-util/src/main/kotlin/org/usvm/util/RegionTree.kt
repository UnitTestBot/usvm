package org.usvm.util

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf

/**
 * Region tree is a data structures storing collection of keys by abstract regions.
 * It maintains the following two invariants:
 * (1) all sibling regions are pairwise disjoint
 * (2) all child regions are included into parent region
 */
class RegionTree<Key, Reg>(val entries: PersistentMap<Reg, Pair<Key, RegionTree<Key, Reg>>>)
        where Reg: Region<Reg>
{
    val isEmpty = entries.isEmpty()

    /**
     * Splits region tree into two trees: completely covered by [region] and disjoint with [region].
     * Filters out nodes for which [filter] returns true.
     */
    private fun splitRecursively(region : Reg, filter: (Key) -> Boolean):  Pair<RegionTree<Key, Reg>, RegionTree<Key, Reg>>
    {
        if (isEmpty)
            return Pair(this, this)
        val entry = entries.get(region)
        if (entry === null) {
            // No such region. Dot it slow way: iterate all entries, group them into:
            // (1) included by [region], (2) disjoint with [region], (3) partially intersected with [region].
            // For nodes in group (3), repeat process recursively.
            val included = mutableListOf<Pair<Reg, Pair<Key, RegionTree<Key, Reg>>>>()
            val disjoint = mutableListOf<Pair<Reg, Pair<Key, RegionTree<Key, Reg>>>>()
            val intersected = mutableListOf<Pair<Reg, Pair<Key, RegionTree<Key, Reg>>>>()
            val groups = mutableMapOf(
                Pair(Pair(RegionComparisonResult.INCLUDES, true), included),
                Pair(Pair(RegionComparisonResult.DISJOINT, true), disjoint),
                Pair(Pair(RegionComparisonResult.INTERSECTS, true), intersected)
            )
            entries.entries.groupByTo(groups, {
                if (filter(it.value.first)) Pair(RegionComparisonResult.INCLUDES, false)
                else Pair(region.compare(it.key), true)}, { Pair(it.key, it.value) })
            var includedMap = persistentHashMapOf(*included.toTypedArray())
            var disjointMap = persistentHashMapOf(*disjoint.toTypedArray())
            intersected.forEach {
                val (splitIncluded, splitDisjoint) = it.second.second.splitRecursively(region, filter)
                val includedReg = it.first.intersect(region)
                val disjointReg = it.first.subtract(region)
                includedMap = includedMap.put(includedReg, Pair(it.second.first, splitIncluded))
                disjointMap = disjointMap.put(disjointReg, Pair(it.second.first, splitDisjoint))
            }
            return Pair(RegionTree(includedMap), RegionTree(disjointMap))
        } else {
            // If we have precisely such region in tree, then all its siblings are disjoint by invariant (1).
            // So just return (node storing the region, rest of its siblings)
            val inside = if (filter(entry.first)) persistentHashMapOf() else persistentHashMapOf(Pair(region, entry))
            val outside = entries.remove(region)
            return Pair(RegionTree(inside), RegionTree(outside))
        }
    }

    /**
     * Returns subtree completely included into [region]
     */
    fun localize(region: Reg): RegionTree<Key, Reg> = splitRecursively(region) { false }.first

    /**
     * Places ([region], [key]) into the tree, preserving its invariants
     */
    fun write(region: Reg, key: Key): RegionTree<Key, Reg> {
        val (included, disjoint) = splitRecursively(region) {key == it}
        return RegionTree(disjoint.entries.put(region, Pair(key, included)))
    }

    // TODO: add collection operations, like map, fold, filter, flatten, etc...

    private fun checkInvariantRecursively(parentRegion: Reg?): Boolean {
        // Invariant (2): all child regions are included into parent region
        val invariant2 = parentRegion === null || entries.entries.all {parentRegion.compare(it.key) == RegionComparisonResult.INCLUDES}
        return invariant2 &&
                entries.entries.all {entry ->
                    //  Invariant (1): all sibling regions are pairwise disjoint
                    val invariant1 = entries.entries.all {
                            other -> other.key === entry.key ||
                            entry.key.compare(other.key) == RegionComparisonResult.DISJOINT
                    }
                    return invariant1 && entry.value.second.checkInvariantRecursively(entry.key)
                }
    }

    fun checkInvariant() {
        if (!checkInvariantRecursively(null))
            throw Exception("The invariant of region tree is violated!")
    }
}

fun <Key, Reg: Region<Reg>> emptyRegionTree() = RegionTree<Key, Reg>(persistentHashMapOf())
