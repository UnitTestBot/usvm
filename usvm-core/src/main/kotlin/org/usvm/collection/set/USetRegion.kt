package org.usvm.collection.set

import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.uctx
import org.usvm.regions.Region

data class USetEntryLValue<SetType, KeySort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    val setRef: UHeapRef,
    val setKey: UExpr<KeySort>,
    val setType: SetType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : ULValue<USetEntryLValue<SetType, KeySort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = keySort.uctx.boolSort

    override val memoryRegionId: UMemoryRegionId<USetEntryLValue<SetType, KeySort, Reg>, UBoolSort>
        get() = USetRegionId(keySort, setType, keyInfo)

    override val key: USetEntryLValue<SetType, KeySort, Reg>
        get() = this
}

data class USetRegionId<SetType, KeySort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    val setType: SetType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : UMemoryRegionId<USetEntryLValue<SetType, KeySort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = keySort.uctx.boolSort

    override fun emptyRegion(): UMemoryRegion<USetEntryLValue<SetType, KeySort, Reg>, UBoolSort> {
        TODO("Not yet implemented")
    }
}

interface USetRegion<SetType, KeySort : USort, Reg : Region<Reg>> :
    UMemoryRegion<USetEntryLValue<SetType, KeySort, Reg>, UBoolSort> {

    fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: SetType,
        keySort: KeySort,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
        guard: UBoolExpr,
    ): USetRegion<SetType, KeySort, Reg>
}
