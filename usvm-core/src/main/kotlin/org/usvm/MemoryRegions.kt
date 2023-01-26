package org.usvm

import org.usvm.regions.*

//region Memory keys

interface UMemoryKey<Reg: Region<Reg>> {
    val region: Reg

    /**
     * Transforms this key by replacing all occurring UExpr.
     * Used for weakest preconditions calculation.
     */
    fun <Sort: USort> map(mapper: (UExpr<Sort>) -> UExpr<Sort>)
}

typealias UHeapAddressRegion = TrivialRegion

class UHeapAddressKey(val address: UHeapRef): UMemoryKey<UHeapAddressRegion> {
    override val region: UHeapAddressRegion
        // TODO: Return (-inf, 0) for symbolic input addresses
        get() = TODO("Not yet implemented")

    override fun <Sort: USort> map(mapper: (UExpr<Sort>) -> UExpr<Sort>) {
        TODO("Not yet implemented")
    }
}

typealias UArrayIndexRegion = CartesianRegion<UHeapAddressRegion, SetRegion<Long>>
class UArrayIndexKey(val heapAddress: UHeapRef, val index: USizeExpr): UMemoryKey<UArrayIndexRegion> {
    override val region: UArrayIndexRegion
        // TODO: Return Z for symbolic indices
        get() = TODO("Not yet implemented")

    override fun <Sort: USort> map(mapper: (UExpr<Sort>) -> UExpr<Sort>) {
        TODO("Not yet implemented")
    }
}

//endregion

//region Memory region

class UUpdateTreeNode<out Key, out Value>(val key: Key, val value: Value) {
    override fun equals(other: Any?): Boolean =
        other is UUpdateTreeNode<*, *> && key == other.key

    override fun hashCode(): Int = key?.hashCode() ?: 0
}

data class UMemoryRegion<Key: UMemoryKey<Reg>, Reg: Region<Reg>, Sort: USort>(
    val sort: Sort,
    val updates: RegionTree<UUpdateTreeNode<Key, UExpr<Sort>>, Reg>,
    val defaultValue: UExpr<Sort>? // If defaultValue = null then this region is filled with symbolics
)
{
    fun read(key: Key, instantiate: (UMemoryRegion<Key, Reg, Sort>) -> UExpr<Sort>): UExpr<Sort> {
        val reg = key.region
        val tree = updates.localize(reg)
        if (tree.isEmpty && defaultValue !== null) {
            return defaultValue
        } else {
            if (tree.entries.size == 1) {
                val entry = tree.entries[reg]?.first
                if (entry?.key == key) {
                    return entry.value
                }
            }
            return instantiate(UMemoryRegion(sort, tree, defaultValue))
        }
    }

    fun write(key: Key, value: UExpr<Sort>): UMemoryRegion<Key, Reg, Sort> {
        // TODO: assert written value is a subtype of region type
        val newUpdates = updates.write(key.region, UUpdateTreeNode(key, value))
        return UMemoryRegion(sort, newUpdates, defaultValue)
    }

}

fun <Key: UMemoryKey<Reg>, Reg: Region<Reg>, Sort: USort> emptyRegion(sort: Sort) =
    UMemoryRegion<Key, Reg, Sort>(sort, emptyRegionTree(), null)


typealias UVectorMemoryRegion = UMemoryRegion<UHeapAddressKey, UHeapAddressRegion, USort>
typealias UArrayMemoryRegion = UMemoryRegion<UArrayIndexKey, UArrayIndexRegion, USort>
typealias UArrayLengthMemoryRegion = UMemoryRegion<UHeapAddressKey, UHeapAddressRegion, USizeSort>

//endregion