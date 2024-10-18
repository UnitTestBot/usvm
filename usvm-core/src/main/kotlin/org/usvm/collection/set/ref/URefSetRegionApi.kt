package org.usvm.collection.set.ref

import org.usvm.UBoolExpr
import org.usvm.UHeapRef
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

    val newRegion = region.union(srcRef, dstRef, guard, ownership)
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
