package org.usvm.collection.map.ref

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USetRegionId
import org.usvm.memory.UWritableMemory

fun <MapType, ValueSort : USort> UWritableMemory<*>.refMapMerge(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    mapType: MapType,
    sort: ValueSort,
    keySet: USetRegionId<MapType, UAddressSort, *>,
    guard: UBoolExpr
) {
    val regionId = URefMapRegionId(sort, mapType)
    val region = getRegion(regionId) as URefMapRegion<MapType, ValueSort>
    val newRegion = region.merge(srcRef, dstRef, mapType, sort, keySet, guard)
    setRegion(regionId, newRegion)
}
