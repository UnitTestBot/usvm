package org.usvm.api.collection

import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.map.ref.refMapMerge
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.collection.set.ref.refSetUnion
import org.usvm.memory.map
import org.usvm.uctx

object ObjectMapCollectionApi {
    fun <MapType, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.mkSymbolicObjectMap(
        mapType: MapType,
    ): UHeapRef = calcOnState {
        with(memory.ctx) {
            val ref = memory.alloc(mapType)
            val length = UMapLengthLValue(ref, mapType)
            memory.write(length, mkSizeExpr(0), trueExpr)
            ref
        }
    }

    // todo: input map size can be inconsistent with contains
    fun <MapType, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.symbolicObjectMapSize(
        mapRef: UHeapRef,
        mapType: MapType,
    ): USizeExpr? = mapRef.map(
        concreteMapper = { concreteMapRef ->
            calcOnState { memory.read(UMapLengthLValue(concreteMapRef, mapType)) }
        },
        symbolicMapper = { symbolicMapRef ->
            val length = calcOnState { memory.read(UMapLengthLValue(symbolicMapRef, mapType)) }
            val boundConstraint = with(length.uctx) { mkBvSignedGreaterOrEqualExpr(length, mkSizeExpr(0)) }
            assert(boundConstraint) ?: return null
            length
        }
    )

    fun <MapType, Sort : USort, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.symbolicObjectMapGet(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
        sort: Sort,
    ): UExpr<Sort> = calcOnState {
        memory.read(URefMapEntryLValue(sort, mapRef, key, mapType))
    }

    fun <MapType, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.symbolicObjectMapContains(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
    ): UBoolExpr = calcOnState {
        memory.read(URefSetEntryLValue(mapRef, key, mapType))
    }

    fun <MapType, Sort : USort, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.symbolicObjectMapPut(
        mapRef: UHeapRef,
        key: UHeapRef,
        value: UExpr<Sort>,
        mapType: MapType,
        sort: Sort,
    ): Unit? {
        val mapContainsLValue = URefSetEntryLValue(mapRef, key, mapType)
        val currentSize = symbolicObjectMapSize(mapRef, mapType) ?: return null

        return calcOnState {
            with(memory.ctx) {
                val keyIsInMap = memory.read(mapContainsLValue)
                val keyIsNew = mkNot(keyIsInMap)

                memory.write(URefMapEntryLValue(sort, mapRef, key, mapType), value, guard = trueExpr)
                memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

                val updatedSize = mkBvAddExpr(currentSize, mkSizeExpr(1))
                memory.write(UMapLengthLValue(mapRef, mapType), updatedSize, keyIsNew)
            }
        }
    }

    fun <MapType, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
    ): Unit? {
        val currentSize = symbolicObjectMapSize(mapRef, mapType) ?: return null

        return calcOnState {
            with(memory.ctx) {
                val mapContainsLValue = URefSetEntryLValue(mapRef, key, mapType)

                val keyIsInMap = memory.read(mapContainsLValue)

                // todo: skip values update?
                memory.write(mapContainsLValue, rvalue = falseExpr, guard = trueExpr)

                val updatedSize = mkBvSubExpr(currentSize, mkSizeExpr(1))
                memory.write(UMapLengthLValue(mapRef, mapType), updatedSize, keyIsInMap)
            }
        }
    }

    fun <MapType, Sort : USort, State : UState<MapType, *, *, *, State>> StepScope<State, MapType, *>.symbolicObjectMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        mapType: MapType,
        sort: Sort,
    ): Unit? {
        val srcMapSize = symbolicObjectMapSize(srcRef, mapType) ?: return null
        val dstMapSize = symbolicObjectMapSize(dstRef, mapType) ?: return null

        calcOnState {
            with(memory.ctx) {
                val containsSetId = URefSetRegionId(mapType, sort.uctx.boolSort)
                memory.refMapMerge(srcRef, dstRef, mapType, sort, containsSetId, guard = trueExpr)
                memory.refSetUnion(srcRef, dstRef, mapType, guard = trueExpr)
            }
        }
        return with(dstRef.uctx) {
            // todo: precise map size approximation?
            // val sizeLowerBound = mkIte(mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
            val sizeUpperBound = mkBvAddExpr(srcMapSize, dstMapSize)

            calcOnState {
                memory.write(UMapLengthLValue(dstRef, mapType), sizeUpperBound, guard = trueExpr)
            }
        }
    }
}
