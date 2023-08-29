package org.usvm.api.collection

import io.ksmt.utils.mkFreshConst
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.map.ref.refMapMerge
import org.usvm.collection.set.USetEntryLValue
import org.usvm.collection.set.USetRegionId
import org.usvm.collection.set.setUnion
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.map

object ObjectMapCollectionApi {
    fun <MapType> UState<MapType, *, *, *, *>.mkSymbolicObjectMap(
        mapType: MapType
    ): UHeapRef = with(memory.ctx) {
        val ref = memory.alloc(mapType)
        val length = UMapLengthLValue(ref, mapType)
        memory.write(length, mkSizeExpr(0), trueExpr)
        return ref
    }

    // todo: input map size can be inconsistent with contains
    fun <MapType> UState<MapType, *, *, *, *>.symbolicObjectMapSize(
        mapRef: UHeapRef,
        mapType: MapType
    ): USizeExpr = with(memory.ctx) {
        mapRef.map(
            concreteMapper = { concreteMapRef ->
                memory.read(UMapLengthLValue(concreteMapRef, mapType))
            },
            symbolicMapper = { symbolicMapRef ->
                memory.read(UMapLengthLValue(symbolicMapRef, mapType)).also {
                    pathConstraints += mkBvSignedGreaterOrEqualExpr(it, mkSizeExpr(0))
                }
            }
        )
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *>.symbolicObjectMapGet(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
        sort: Sort
    ): UExpr<Sort> = with(memory.ctx) {
        memory.read(URefMapEntryLValue(sort, mapRef, key, mapType))
    }

    fun <MapType> UState<MapType, *, *, *, *>.symbolicObjectMapContains(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType
    ): UBoolExpr = with(memory.ctx) {
        memory.read(USetEntryLValue(addressSort, mapRef, key, mapType, UHeapRefKeyInfo))
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *>.symbolicObjectMapPut(
        mapRef: UHeapRef,
        key: UHeapRef,
        value: UExpr<Sort>,
        mapType: MapType,
        sort: Sort
    ): Unit = with(memory.ctx) {
        val mapContainsLValue = USetEntryLValue(addressSort, mapRef, key, mapType, UHeapRefKeyInfo)

        val keyIsInMap = memory.read(mapContainsLValue)
        val keyIsNew = mkNot(keyIsInMap)

        memory.write(URefMapEntryLValue(sort, mapRef, key, mapType), value, guard = trueExpr)
        memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

        val currentSize = symbolicObjectMapSize(mapRef, mapType)
        val updatedSize = mkBvAddExpr(currentSize, mkSizeExpr(1))
        memory.write(UMapLengthLValue(mapRef, mapType), updatedSize, keyIsNew)
    }

    fun <MapType> UState<MapType, *, *, *, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType
    ): Unit = with(memory.ctx) {
        val mapContainsLValue = USetEntryLValue(addressSort, mapRef, key, mapType, UHeapRefKeyInfo)

        val keyIsInMap = memory.read(mapContainsLValue)

        // todo: skip values update?
        memory.write(mapContainsLValue, rvalue = falseExpr, guard = trueExpr)

        val currentSize = symbolicObjectMapSize(mapRef, mapType)
        val updatedSize = mkBvSubExpr(currentSize, mkSizeExpr(1))
        memory.write(UMapLengthLValue(mapRef, mapType), updatedSize, keyIsInMap)
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *>.symbolicObjectMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        mapType: MapType,
        sort: Sort
    ): Unit = with(memory.ctx) {
        val containsSetId = USetRegionId(addressSort, mapType, UHeapRefKeyInfo)

        memory.refMapMerge(srcRef, dstRef, mapType, sort, containsSetId, guard = trueExpr)

        memory.setUnion(srcRef, dstRef, mapType, addressSort, UHeapRefKeyInfo, guard = trueExpr)

        // todo: precise map size approximation?
        val mergedMapSize = sizeSort.mkFreshConst("mergedMapSize")
        val srcMapSize = symbolicObjectMapSize(srcRef, mapType)
        val dstMapSize = symbolicObjectMapSize(dstRef, mapType)
        val sizeLowerBound = mkIte(mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
        val sizeUpperBound = mkBvAddExpr(srcMapSize, dstMapSize)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeLowerBound)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeUpperBound)
        memory.write(UMapLengthLValue(dstRef, mapType), mergedMapSize, guard = trueExpr)
    }
}
