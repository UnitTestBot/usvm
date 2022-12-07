package org.usvm

import org.usvm.regions.*

//region Memory keys

interface UMemoryKey<Reg: Region<Reg>> {
    val region: Reg

    /**
     * Transforms this key by replacing all occurring UExpr.
     * Used for weakest preconditions calculation.
     */
    fun map(mapper: (UExpr) -> UExpr)
}

typealias UHeapAddressRegion = TrivialRegion

class UHeapAddressKey(val address: UHeapRef): UMemoryKey<UHeapAddressRegion> {
    override val region: UHeapAddressRegion
        // TODO: Return (-inf, 0) for symbolic input addresses
        get() = TODO("Not yet implemented")

    override fun map(mapper: (UExpr) -> UExpr) {
        TODO("Not yet implemented")
    }
}

typealias UArrayIndexRegion = CartesianRegion<UHeapAddressRegion, SetRegion<Long>>
class UArrayIndexKey(val heapAddress: UHeapRef, val index: USizeExpr): UMemoryKey<UArrayIndexRegion> {
    override val region: UArrayIndexRegion
        // TODO: Return Z for symbolic indices
        get() = TODO("Not yet implemented")

    override fun map(mapper: (UExpr) -> UExpr) {
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

data class UMemoryRegion<Key: UMemoryKey<Reg>, Reg: Region<Reg>, Value>(
    val sort: USort,
    val updates: RegionTree<UUpdateTreeNode<Key, Value>, Reg>,
    val defaultValue: Value? // If defaultValue = null then this region is filled with symbolics
)
{
    fun read(key: Key, instantiate: (UMemoryRegion<Key, Reg, Value>) -> Value): Value {
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

    fun write(key: Key, value: Value): UMemoryRegion<Key, Reg, Value> {
        // TODO: assert written value is a subtype of region type
        val newUpdates = updates.write(key.region, UUpdateTreeNode(key, value))
        return UMemoryRegion(sort, newUpdates, defaultValue)
    }

}

fun <Key: UMemoryKey<Reg>, Reg: Region<Reg>, Value> emptyRegion(sort: USort) =
    UMemoryRegion<Key, Reg, Value>(sort, emptyRegionTree(), null)


typealias UVectorMemoryRegion = UMemoryRegion<UHeapAddressKey, UHeapAddressRegion, UExpr>
typealias UArrayMemoryRegion = UMemoryRegion<UArrayIndexKey, UArrayIndexRegion, UExpr>
typealias UArrayLengthMemoryRegion = UMemoryRegion<UHeapAddressKey, UHeapAddressRegion, USizeExpr>

//endregion