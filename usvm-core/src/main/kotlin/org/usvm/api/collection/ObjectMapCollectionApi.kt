package org.usvm.api.collection

import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.map.ref.refMapMerge
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.collection.set.ref.refSetUnion
import org.usvm.memory.mapWithStaticAsConcrete
import org.usvm.uctx
import org.usvm.withSizeSort

object ObjectMapCollectionApi {
    fun <MapType, USizeSort : USort> UState<MapType, *, *, *, *, *>.mkSymbolicObjectMap(
        mapType: MapType,
    ): UHeapRef = with(memory.ctx.withSizeSort<USizeSort>()) {
        val ref = memory.allocConcrete(mapType)
        val length = UMapLengthLValue<MapType, USizeSort>(ref, mapType)
        memory.write(length, mkSizeExpr(0), trueExpr)
        ref
    }

    /**
     * Map size may be incorrect for input maps.
     * Use [ensureObjectMapSizeCorrect] to guarantee that map size is correct.
     * todo: input map size can be inconsistent with contains
     * */
    fun <MapType, USizeSort : USort> UState<MapType, *, *, *, *, *>.symbolicObjectMapSize(
        mapRef: UHeapRef,
        mapType: MapType,
    ): UExpr<USizeSort> = memory.read(UMapLengthLValue(mapRef, mapType))

    fun <MapType, State : UState<MapType, *, *, *, *, State>, USizeSort : USort> StepScope<State, MapType, *>.ensureObjectMapSizeCorrect(
        mapRef: UHeapRef,
        mapType: MapType,
    ): Unit? {
        mapRef.mapWithStaticAsConcrete(
            concreteMapper = {
                // Concrete map size is always correct
                it
            },
            symbolicMapper = { symbolicMapRef ->
                val length = calcOnState { memory.read(UMapLengthLValue<MapType, USizeSort>(symbolicMapRef, mapType)) }
                with(length.uctx.withSizeSort<USizeSort>()) {
                    val boundConstraint = mkSizeGeExpr(length, mkSizeExpr(0))
                    // Map size must be correct regardless of guard
                    assert(boundConstraint) ?: return null
                }
                symbolicMapRef
            }
        )
        return Unit
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *, *>.symbolicObjectMapGet(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
        sort: Sort,
    ): UExpr<Sort> = memory.read(URefMapEntryLValue(sort, mapRef, key, mapType))

    fun <MapType> UState<MapType, *, *, *, *, *>.symbolicObjectMapContains(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
    ): UBoolExpr = memory.read(URefSetEntryLValue(mapRef, key, mapType))

    fun <MapType, Sort : USort, USizeSort : USort> UState<MapType, *, *, *, *, *>.symbolicObjectMapPut(
        mapRef: UHeapRef,
        key: UHeapRef,
        value: UExpr<Sort>,
        mapType: MapType,
        sort: Sort,
    ) = with(memory.ctx.withSizeSort<USizeSort>()) {
        val mapContainsLValue = URefSetEntryLValue(mapRef, key, mapType)
        val currentSize = symbolicObjectMapSize<MapType, USizeSort>(mapRef, mapType)

        val keyIsInMap = memory.read(mapContainsLValue)
        val keyIsNew = mkNot(keyIsInMap)

        memory.write(URefMapEntryLValue(sort, mapRef, key, mapType), value, guard = trueExpr)
        memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

        val updatedSize = mkSizeAddExpr(currentSize, mkSizeExpr(1))
        memory.write(UMapLengthLValue(mapRef, mapType), updatedSize, keyIsNew)
    }

    fun <MapType, USizeSort : USort> UState<MapType, *, *, *, *, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
    ) = with(memory.ctx.withSizeSort<USizeSort>()) {
        val mapContainsLValue = URefSetEntryLValue(mapRef, key, mapType)
        val currentSize = symbolicObjectMapSize<MapType, USizeSort>(mapRef, mapType)

        val keyIsInMap = memory.read(mapContainsLValue)

        // todo: skip values update?
        memory.write(mapContainsLValue, rvalue = falseExpr, guard = trueExpr)

        val updatedSize = mkSizeSubExpr(currentSize, mkSizeExpr(1))
        memory.write(UMapLengthLValue(mapRef, mapType), updatedSize, keyIsInMap)
    }

    fun <MapType, Sort : USort, USizeSort : USort> UState<MapType, *, *, *, *, *>.symbolicObjectMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        mapType: MapType,
        sort: Sort,
    ) = with(memory.ctx.withSizeSort<USizeSort>()) {
        val srcMapSize = symbolicObjectMapSize<MapType, USizeSort>(srcRef, mapType)
        val dstMapSize = symbolicObjectMapSize<MapType, USizeSort>(dstRef, mapType)

        val containsSetId = URefSetRegionId(mapType, sort.uctx.boolSort)
        memory.refMapMerge(srcRef, dstRef, mapType, sort, containsSetId, guard = trueExpr)
        memory.refSetUnion(srcRef, dstRef, mapType, guard = trueExpr)

        // todo: precise map size approximation?
        // val sizeLowerBound = mkIte(mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
        val sizeUpperBound = mkSizeAddExpr(srcMapSize, dstMapSize)
        memory.write(UMapLengthLValue(dstRef, mapType), sizeUpperBound, guard = trueExpr)
    }
}
