package org.usvm.api.collection

import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.api.refSetContainsElement
import org.usvm.collection.set.length.USetLengthLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.map.ref.refMapMerge
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.collection.set.ref.refSetEntries
import org.usvm.collection.set.ref.refSetIntersectionSize
import org.usvm.collection.set.ref.refSetUnion
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.memory.mapWithStaticAsConcrete
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
import org.usvm.uctx
import org.usvm.utils.logAssertFailure

object ObjectMapCollectionApi {
    fun <MapType, USizeSort : USort, Ctx: UContext<USizeSort>> UState<MapType, *, *, Ctx, *, *>.mkSymbolicObjectMap(
        mapType: MapType,
    ): UHeapRef = with(ctx) {
        val ref = memory.allocConcrete(mapType)
        val length = USetLengthLValue(ref, mapType, sizeSort)
        memory.write(length, mkSizeExpr(0), trueExpr)
        ref
    }

    /**
     * Map size may be incorrect for input maps.
     * Use [ensureObjectMapSizeCorrect] to guarantee that map size is correct.
     * todo: input map size can be inconsistent with contains
     * */
    fun <MapType, USizeSort : USort, Ctx: UContext<USizeSort>> UState<MapType, *, *, Ctx, *, *>.symbolicObjectMapSize(
        mapRef: UHeapRef,
        mapType: MapType,
    ): UExpr<USizeSort> = memory.read(USetLengthLValue(mapRef, mapType, ctx.sizeSort))

    fun <MapType, State, USizeSort : USort, Ctx> StepScope<State, MapType, *, *>.ensureObjectMapSizeCorrect(
        mapRef: UHeapRef,
        mapType: MapType,
    ): Unit? where State : UState<MapType, *, *, Ctx, *, State>, Ctx : UContext<USizeSort> {
        mapRef.mapWithStaticAsConcrete(
            concreteMapper = {
                // Concrete map size is always correct
                it
            },
            symbolicMapper = { symbolicMapRef ->
                val length = calcOnState { memory.read(USetLengthLValue(symbolicMapRef, mapType, ctx.sizeSort)) }
                val ctx = calcOnState { ctx }
                with(ctx) {
                    val boundConstraint = mkSizeGeExpr(length, mkSizeExpr(0))
                    // Map size must be correct regardless of guard
                    assert(boundConstraint)
                        .logAssertFailure { "Constraint violation: SymbolicMap size correctness constraint" }
                        ?: return null
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
    ): UBoolExpr = memory.refSetContainsElement(mapRef, key, mapType)

    fun <MapType> UState<MapType, *, *, *, *, *>.symbolicObjectMapAnyKey(
        mapRef: UHeapRef,
        mapType: MapType,
    ): UHeapRef {
        val allKeys = memory.refSetEntries(mapRef, mapType)
        val symbolicKeys = mutableListOf<Pair<UHeapRef, UBoolExpr>>()
        for (entry in allKeys.entries) {
            val key = entry.setElement
            val contains = symbolicObjectMapContains(mapRef, key, mapType)
            when {
                contains.isTrue -> return key
                contains.isFalse -> continue
                else -> symbolicKeys += key to contains
            }
        }

        val defaultKey = if (allKeys.isInput) {
            // New symbolic key
            makeSymbolicRefUntyped()
        } else {
            // All map keys are known -> defaultKey should be unreachable.
            // Create fresh key, which is definitely not in map.
            ctx.mkConcreteHeapRef(ctx.addressCounter.freshAllocatedAddress())
        }

        return symbolicKeys.fold(defaultKey) { result, (key, contains) ->
            ctx.mkIte(contains, key, result)
        }
    }

    fun <MapType, Sort : USort, USizeSort : USort, Ctx: UContext<USizeSort>> UState<MapType, *, *, Ctx, *, *>.symbolicObjectMapPut(
        mapRef: UHeapRef,
        key: UHeapRef,
        value: UExpr<Sort>,
        mapType: MapType,
        sort: Sort,
    ) = with(ctx) {
        val mapContainsLValue = URefSetEntryLValue(mapRef, key, mapType)
        val currentSize = symbolicObjectMapSize(mapRef, mapType)

        val keyIsInMap = memory.read(mapContainsLValue)
        val keyIsNew = mkNot(keyIsInMap)

        memory.write(URefMapEntryLValue(sort, mapRef, key, mapType), value, guard = trueExpr)
        memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

        val updatedSize = mkSizeAddExpr(currentSize, mkSizeExpr(1))
        memory.write(USetLengthLValue(mapRef, mapType, sizeSort), updatedSize, keyIsNew)
    }

    fun <MapType, USizeSort : USort, Ctx: UContext<USizeSort>> UState<MapType, *, *, Ctx, *, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
    ) = with(ctx) {
        val mapContainsLValue = URefSetEntryLValue(mapRef, key, mapType)
        val currentSize = symbolicObjectMapSize(mapRef, mapType)

        val keyIsInMap = memory.read(mapContainsLValue)

        // todo: skip values update?
        memory.write(mapContainsLValue, rvalue = falseExpr, guard = trueExpr)

        val updatedSize = mkSizeSubExpr(currentSize, mkSizeExpr(1))
        memory.write(USetLengthLValue(mapRef, mapType, sizeSort), updatedSize, keyIsInMap)
    }

    fun <MapType, Sort : USort, USizeSort : USort, Ctx: UContext<USizeSort>> UState<MapType, *, *, Ctx, *, *>.symbolicObjectMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        mapType: MapType,
        sort: Sort,
    ) = with(ctx) {
        val srcMapSize = symbolicObjectMapSize(srcRef, mapType)
        val dstMapSize = symbolicObjectMapSize(dstRef, mapType)

        val mapIntersectionSize = memory.refSetIntersectionSize<_, USizeSort>(dstRef, srcRef, mapType)

        val containsSetId = URefSetRegionId(mapType, sort.uctx.boolSort)
        memory.refMapMerge(srcRef, dstRef, mapType, sort, containsSetId, guard = trueExpr)
        memory.refSetUnion(srcRef, dstRef, mapType, guard = trueExpr)

        val mergedMapSize = mkSizeSubExpr(mkSizeAddExpr(srcMapSize, dstMapSize), mapIntersectionSize)
        memory.write(USetLengthLValue(dstRef, mapType, sizeSort), mergedMapSize, guard = trueExpr)
    }
}
