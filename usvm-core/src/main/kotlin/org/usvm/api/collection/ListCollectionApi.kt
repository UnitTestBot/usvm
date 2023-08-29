package org.usvm.api.collection

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.writeArrayIndex
import org.usvm.api.writeArrayLength
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
                memory.readArrayLength(symbolicListRef, listType).also {
                    pathConstraints += mkBvSignedGreaterOrEqualExpr(it, mkSizeExpr(0))
                }
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

        val srcIndex = index
        val indexAfterInsert = mkBvAddExpr(index, mkSizeExpr(1))
        val lastIndexAfterInsert = currentSize

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

        val firstIndexAfterRemove = mkBvAddExpr(index, mkSizeExpr(1))
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
