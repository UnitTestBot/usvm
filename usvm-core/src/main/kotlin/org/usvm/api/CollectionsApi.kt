package org.usvm.api

import io.ksmt.utils.mkFreshConst
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.collection.map.length.USymbolicMapLengthRef
import org.usvm.collection.map.ref.USymbolicRefMapEntryRef
import org.usvm.collection.map.ref.symbolicRefMapMerge
import org.usvm.collection.set.USymbolicSetEntryRef
import org.usvm.collection.set.USymbolicSetRegionId
import org.usvm.collection.set.symbolicSetUnion
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.map

object ListCollectionApi {
    fun <ListType> UState<ListType, *, *, *, *>.mkSymbolicList(
        listType: ListType
    ): UHeapRef = with(memory.ctx) {
        val ref = memory.alloc(listType)
        memory.writeArrayLength(ref, mkSizeExpr(0), listType)
        return ref
    }

    fun <ListType> UState<ListType, *, *, *, *>.symbolicListSize(
        listRef: UHeapRef,
        listType: ListType
    ): USizeExpr = with(memory.ctx) {
        listRef.map(
            concreteMapper = { concreteListRef ->
                memory.readArrayLength(concreteListRef, listType)
            },
            symbolicMapper = { symbolicListRef ->
                ensureAtLeasZero(memory.readArrayLength(symbolicListRef, listType))
            }
        )
    }

    fun <ListType, Sort : USort> UState<ListType, *, *, *, *>.symbolicListGet(
        listRef: UHeapRef,
        index: USizeExpr,
        listType: ListType,
        sort: Sort
    ): UExpr<Sort> = with(memory.ctx) {
        memory.readArrayIndex(listRef, index, listType, sort)
    }

    fun <ListType, Sort : USort> UState<ListType, *, *, *, *>.symbolicListAdd(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        value: UExpr<Sort>
    ): Unit = with(memory.ctx) {
        val size = symbolicListSize(listRef, listType)

        memory.writeArrayIndex(listRef, size, listType, sort, value, guard = trueExpr)

        val updatedSize = mkBvAddExpr(size, mkSizeExpr(1))
        memory.writeArrayLength(listRef, updatedSize, listType)
    }

    fun <ListType, Sort : USort> UState<ListType, *, *, *, *>.symbolicListSet(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: USizeExpr,
        value: UExpr<Sort>
    ) = with(memory.ctx) {
        memory.writeArrayIndex(listRef, index, listType, sort, value, guard = trueExpr)
    }

    fun <ListType, Sort : USort> UState<ListType, *, *, *, *>.symbolicListInsert(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: USizeExpr,
        value: UExpr<Sort>
    ): Unit = with(memory.ctx) {
        val currentSize = symbolicListSize(listRef, listType)

        val srcIndex = mkBvAddExpr(index, mkSizeExpr(2))
        val indexAfterInsert = mkBvAddExpr(index, mkSizeExpr(1))
        val lastIndexAfterInsert = mkBvSubExpr(currentSize, mkSizeExpr(1))

        memory.memcpy(
            srcRef = listRef,
            dstRef = listRef,
            type = listType,
            elementSort = sort,
            fromSrcIdx = srcIndex,
            fromDstIdx = indexAfterInsert,
            toDstIdx = lastIndexAfterInsert,
            guard = trueExpr
        )

        memory.writeArrayIndex(listRef, index, listType, sort, value, guard = trueExpr)

        val updatedSize = mkBvAddExpr(currentSize, mkSizeExpr(1))
        memory.writeArrayLength(listRef, updatedSize, listType)
    }

    fun <ListType, Sort : USort> UState<ListType, *, *, *, *>.symbolicListRemove(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: USizeExpr
    ): Unit = with(memory.ctx) {
        val currentSize = symbolicListSize(listRef, listType)

        val firstIndexAfterRemove = mkBvSubExpr(index, mkSizeExpr(1))
        val lastIndexAfterRemove = mkBvSubExpr(currentSize, mkSizeExpr(2))

        memory.memcpy(
            srcRef = listRef,
            dstRef = listRef,
            type = listType,
            elementSort = sort,
            fromSrcIdx = firstIndexAfterRemove,
            fromDstIdx = index,
            toDstIdx = lastIndexAfterRemove,
            guard = trueExpr
        )

        val updatedSize = mkBvSubExpr(currentSize, mkSizeExpr(1))
        memory.writeArrayLength(listRef, updatedSize, listType)
    }

    fun <ListType, Sort : USort> UState<ListType, *, *, *, *>.symbolicListCopyRange(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        srcFrom: USizeExpr,
        dstFrom: USizeExpr,
        length: USizeExpr
    ): Unit = with(memory.ctx) {
        memory.memcpy(
            srcRef = srcRef,
            dstRef = dstRef,
            type = listType,
            elementSort = sort,
            fromSrc = srcFrom,
            fromDst = dstFrom,
            length = length
        )
    }
}

object ObjectMapCollectionApi {
    fun <MapType> UState<MapType, *, *, *, *>.mkSymbolicObjectMap(
        mapType: MapType
    ): UHeapRef = with(memory.ctx) {
        val ref = memory.alloc(mapType)
        val length = USymbolicMapLengthRef(ref, mapType)
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
                memory.read(USymbolicMapLengthRef(concreteMapRef, mapType))
            },
            symbolicMapper = { symbolicMapRef ->
                ensureAtLeasZero(memory.read(USymbolicMapLengthRef(symbolicMapRef, mapType)))
            }
        )
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *>.symbolicObjectMapGet(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType,
        sort: Sort
    ): UExpr<Sort> = with(memory.ctx) {
        memory.read(USymbolicRefMapEntryRef(sort, mapRef, key, mapType))
    }

    fun <MapType> UState<MapType, *, *, *, *>.symbolicObjectMapContains(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType
    ): UBoolExpr = with(memory.ctx) {
        memory.read(USymbolicSetEntryRef(addressSort, mapRef, key, mapType, UHeapRefKeyInfo))
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *>.symbolicObjectMapPut(
        mapRef: UHeapRef,
        key: UHeapRef,
        value: UExpr<Sort>,
        mapType: MapType,
        sort: Sort
    ): Unit = with(memory.ctx) {
        val mapContainsLValue = USymbolicSetEntryRef(addressSort, mapRef, key, mapType, UHeapRefKeyInfo)

        val keyIsInMap = memory.read(mapContainsLValue)
        val keyIsNew = mkNot(keyIsInMap)

        memory.write(USymbolicRefMapEntryRef(sort, mapRef, key, mapType), value, guard = trueExpr)
        memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

        val currentSize = symbolicObjectMapSize(mapRef, mapType)
        val updatedSize = mkBvAddExpr(currentSize, mkSizeExpr(1))
        memory.write(USymbolicMapLengthRef(mapRef, mapType), updatedSize, keyIsNew)
    }

    fun <MapType> UState<MapType, *, *, *, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef,
        mapType: MapType
    ): Unit = with(memory.ctx) {
        val mapContainsLValue = USymbolicSetEntryRef(addressSort, mapRef, key, mapType, UHeapRefKeyInfo)

        val keyIsInMap = memory.read(mapContainsLValue)

        // todo: skip values update?
        memory.write(mapContainsLValue, rvalue = falseExpr, guard = trueExpr)

        val currentSize = symbolicObjectMapSize(mapRef, mapType)
        val updatedSize = mkBvSubExpr(currentSize, mkSizeExpr(1))
        memory.write(USymbolicMapLengthRef(mapRef, mapType), updatedSize, keyIsInMap)
    }

    fun <MapType, Sort : USort> UState<MapType, *, *, *, *>.symbolicObjectMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        mapType: MapType,
        sort: Sort
    ): Unit = with(memory.ctx) {
        val containsSetId = USymbolicSetRegionId(addressSort, mapType, UHeapRefKeyInfo)

        memory.symbolicRefMapMerge(srcRef, dstRef, mapType, sort, containsSetId, guard = trueExpr)

        memory.symbolicSetUnion(srcRef, dstRef, mapType, addressSort, UHeapRefKeyInfo, guard = trueExpr)

        // todo: precise map size approximation?
        val mergedMapSize = sizeSort.mkFreshConst("mergedMapSize")
        val srcMapSize = symbolicObjectMapSize(srcRef, mapType)
        val dstMapSize = symbolicObjectMapSize(dstRef, mapType)
        val sizeLowerBound = mkIte(mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
        val sizeUpperBound = mkBvAddExpr(srcMapSize, dstMapSize)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeLowerBound)
        pathConstraints += mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeUpperBound)
        memory.write(USymbolicMapLengthRef(dstRef, mapType), mergedMapSize, guard = trueExpr)
    }
}

private fun UContext.ensureAtLeasZero(expr: USizeExpr): USizeExpr =
    mkIte(mkBvSignedGreaterOrEqualExpr(expr, mkSizeExpr(0)), expr, mkSizeExpr(0))
