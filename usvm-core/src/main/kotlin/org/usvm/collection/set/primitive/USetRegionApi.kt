package org.usvm.collection.set.primitive

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.regions.Region

internal fun <SetType, KeySort : USort, Reg : Region<Reg>> UWritableMemory<*>.setUnion(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: SetType,
    keySort: KeySort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    guard: UBoolExpr,
) {
    val regionId = USetRegionId(keySort, type, keyInfo)
    val region = getRegion(regionId)

    check(region is USetRegion<SetType, KeySort, Reg>) {
        "setUnion is not applicable to $region"
    }

    val newRegion = region.union(srcRef, dstRef, guard)
    setRegion(regionId, newRegion)
}
