package org.usvm.collection.map.primitive

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.primitive.USetRegion
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.regions.Region

internal fun <MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> UWritableMemory<*>.mapMerge(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    mapType: MapType,
    keySort: KeySort,
    sort: ValueSort,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    keySet: USetRegionId<MapType, KeySort, Nothing>,
    guard: UBoolExpr,
) {
    val regionId = UMapRegionId(keySort, sort, mapType, keyInfo)
    val region = getRegion(regionId)

    check(region is UMapRegion<MapType, KeySort, ValueSort, Reg>) {
        "mapMerge is not applicable to $region"
    }

    val keySetRegion = getRegion(keySet)
    check(keySetRegion is USetRegion<MapType, KeySort, *>) {
        "mapMerge is not applicable to set $region"
    }

    val newRegion = region.merge(srcRef, dstRef, mapType, keySetRegion, guard, ownership)
    setRegion(regionId, newRegion)
}
