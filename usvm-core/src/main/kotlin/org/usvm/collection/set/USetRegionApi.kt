package org.usvm.collection.set

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.util.Region

internal fun <SetType, KeySort : USort, Reg : Region<Reg>> UWritableMemory<*>.setUnion(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: SetType,
    keySort: KeySort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    guard: UBoolExpr,
) {
    val regionId = USetRegionId(keySort, type, keyInfo)
    val region = getRegion(regionId) as USetRegion<SetType, KeySort, Reg>
    val newRegion = region.union(srcRef, dstRef, type, keySort, keyInfo, guard)
    setRegion(regionId, newRegion)
}
