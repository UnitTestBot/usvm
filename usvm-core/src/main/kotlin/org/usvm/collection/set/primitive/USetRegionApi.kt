package org.usvm.collection.set.primitive

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USetRegionId
import org.usvm.memory.UReadOnlyMemory
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

fun <SetType, KeySort : USort, Reg : Region<Reg>> UReadOnlyMemory<*>.setEntries(
    setRef: UHeapRef,
    type: SetType,
    keySort: KeySort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
): UPrimitiveSetEntries<SetType, KeySort, Reg> {
    val regionId = USetRegionId(keySort, type, keyInfo)
    val region = getRegion(regionId) as? USetReadOnlyRegion<SetType, KeySort, Reg>
        ?: return UPrimitiveSetEntries<SetType, KeySort, Reg>().apply { markAsInput() }

    return region.setEntries(setRef)
}

fun <SetType, KeySort : USort, Reg : Region<Reg>, SizeSort : USort> UReadOnlyMemory<*>.setIntersectionSize(
    firstRef: UHeapRef,
    secondRef: UHeapRef,
    type: SetType,
    keySort: KeySort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
): UExpr<SizeSort> {
    val regionId = USetRegionId(keySort, type, keyInfo)
    val region = getRegion(regionId)

    check(region is USetReadOnlyRegion<SetType, KeySort, Reg>) {
        "setIntersectionSize is not applicable to $region"
    }

    return region.setIntersectionSize(firstRef, secondRef)
}
