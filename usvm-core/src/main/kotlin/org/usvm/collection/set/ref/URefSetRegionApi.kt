package org.usvm.collection.set.ref

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UWritableMemory
import org.usvm.uctx

internal fun <SetType> UWritableMemory<*>.refSetUnion(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: SetType,
    guard: UBoolExpr,
) {
    val regionId = URefSetRegionId(type, srcRef.uctx.boolSort)
    val region = getRegion(regionId)

    check(region is URefSetRegion<SetType>) {
        "setUnion is not applicable to $region"
    }

    val newRegion = region.union(srcRef, dstRef, guard)
    setRegion(regionId, newRegion)
}

fun <SetType> UReadOnlyMemory<*>.refSetEntries(
    setRef: UHeapRef,
    type: SetType,
): URefSetEntries<SetType> {
    val regionId = URefSetRegionId(type, setRef.uctx.boolSort)
    val region = getRegion(regionId) as? URefSetReadOnlyRegion<SetType>
        ?: return URefSetEntries<SetType>().apply { markAsInput() }

    return region.setEntries(setRef)
}

fun <SetType, SizeSort : USort> UReadOnlyMemory<*>.refSetIntersectionSize(
    firstRef: UHeapRef,
    secondRef: UHeapRef,
    type: SetType,
): UExpr<SizeSort> {
    val regionId = URefSetRegionId(type, firstRef.uctx.boolSort)
    val region = getRegion(regionId)

    check(region is URefSetReadOnlyRegion<SetType>) {
        "refSetIntersectionSize is not applicable to $region"
    }

    return region.setIntersectionSize(firstRef, secondRef)
}
