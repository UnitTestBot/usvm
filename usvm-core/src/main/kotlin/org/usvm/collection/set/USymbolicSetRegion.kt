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
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import org.usvm.util.Region

data class USymbolicSetEntryRef<SetType, KeySort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    val setRef: UHeapRef,
    val setKey: UExpr<KeySort>,
    val setType: SetType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : ULValue<USymbolicSetEntryRef<SetType, KeySort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = keySort.uctx.boolSort

    override val memoryRegionId: UMemoryRegionId<USymbolicSetEntryRef<SetType, KeySort, Reg>, UBoolSort>
        get() = USymbolicSetRegionId(keySort, setType, keyInfo)

    override val key: USymbolicSetEntryRef<SetType, KeySort, Reg>
        get() = this
}

data class USymbolicSetRegionId<SetType, KeySort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    val setType: SetType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : UMemoryRegionId<USymbolicSetEntryRef<SetType, KeySort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = keySort.uctx.boolSort

    override fun emptyRegion(): UMemoryRegion<USymbolicSetEntryRef<SetType, KeySort, Reg>, UBoolSort> {
        TODO("Not yet implemented")
    }
}

interface USymbolicSetRegion<SetType, KeySort : USort, Reg : Region<Reg>> :
    UMemoryRegion<USymbolicSetEntryRef<SetType, KeySort, Reg>, UBoolSort> {

    fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: SetType,
        keySort: KeySort,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
        guard: UBoolExpr,
    ): USymbolicSetRegion<SetType, KeySort, Reg>
}

internal fun <SetType, KeySort : USort, Reg : Region<Reg>> UWritableMemory<*>.symbolicSetUnion(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: SetType,
    keySort: KeySort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    guard: UBoolExpr,
) {
    val regionId = USymbolicSetRegionId(keySort, type, keyInfo)
    val region = getRegion(regionId) as USymbolicSetRegion<SetType, KeySort, Reg>
    val newRegion = region.union(srcRef, dstRef, type, keySort, keyInfo, guard)
    setRegion(regionId, newRegion)
}
