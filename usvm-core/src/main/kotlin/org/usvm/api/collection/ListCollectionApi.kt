package org.usvm.api.collection

import org.usvm.StepScope
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
import org.usvm.uctx

object ListCollectionApi {
    fun <ListType, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.mkSymbolicList(
        listType: ListType,
    ): UHeapRef = calcOnState {
        with(memory.ctx) {
            val ref = memory.alloc(listType)
            memory.writeArrayLength(ref, mkSizeExpr(0), listType)
            ref
        }
    }

    fun <ListType, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListSize(
        listRef: UHeapRef,
        listType: ListType,
    ): USizeExpr? =
        listRef.map(
            concreteMapper = { concreteListRef ->
                calcOnState { memory.readArrayLength(concreteListRef, listType) }
            },
            symbolicMapper = { symbolicListRef ->
                val length = calcOnState { memory.readArrayLength(symbolicListRef, listType) }
                with(length.uctx) {
                    val boundConstraint = mkBvSignedGreaterOrEqualExpr(length, mkSizeExpr(0))
                    assert(boundConstraint)
                }
                    ?: return null
                length
            }
        )

    fun <ListType, Sort : USort, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListGet(
        listRef: UHeapRef,
        index: USizeExpr,
        listType: ListType,
        sort: Sort,
    ): UExpr<Sort> = calcOnState {
        memory.readArrayIndex(listRef, index, listType, sort)
    }

    fun <ListType, Sort : USort, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListAdd(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        value: UExpr<Sort>,
    ): Unit? {
        val size = symbolicListSize(listRef, listType) ?: return null

        return calcOnState {
            with(memory.ctx) {
                memory.writeArrayIndex(listRef, size, listType, sort, value, guard = trueExpr)
                val updatedSize = mkBvAddExpr(size, mkSizeExpr(1))
                memory.writeArrayLength(listRef, updatedSize, listType)
            }
        }
    }

    fun <ListType, Sort : USort, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListSet(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: USizeExpr,
        value: UExpr<Sort>,
    ) = calcOnState {
        memory.writeArrayIndex(listRef, index, listType, sort, value, guard = memory.ctx.trueExpr)
    }

    fun <ListType, Sort : USort, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListInsert(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: USizeExpr,
        value: UExpr<Sort>,
    ): Unit? {
        val currentSize = symbolicListSize(listRef, listType) ?: return null

        return calcOnState {
            with(memory.ctx) {

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
        }
    }

    fun <ListType, Sort : USort, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListRemove(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: USizeExpr,
    ): Unit? {
        val currentSize = symbolicListSize(listRef, listType) ?: return null

        return calcOnState {
            with(memory.ctx) {
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
        }
    }

    fun <ListType, Sort : USort, State : UState<ListType, *, *, *, State>> StepScope<State, ListType, *>.symbolicListCopyRange(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        srcFrom: USizeExpr,
        dstFrom: USizeExpr,
        length: USizeExpr,
    ): Unit = calcOnState {
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
