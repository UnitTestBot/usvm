package org.usvm.collection.map.ref

import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.ref.URefSetRegion
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.memory.UWritableMemory

internal fun <MapType, ValueSort : USort> UWritableMemory<*>.refMapMerge(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    mapType: MapType,
    sort: ValueSort,
    keySetId: URefSetRegionId<MapType>,
    guard: UBoolExpr
) {
    val regionId = URefMapRegionId(sort, mapType)
    val region = getRegion(regionId)

    check(region is URefMapRegion<MapType, ValueSort>) {
        "refMapMerge is not applicable to $region"
    }

    val keySet = getRegion(keySetId)
    check(keySet is URefSetRegion<MapType>) {
        "refMapMerge is not applicable to set $region"
    }

    val newRegion = region.merge(srcRef, dstRef, mapType, sort, keySet, guard, ownership)
    setRegion(regionId, newRegion)
}
