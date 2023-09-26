package org.usvm.api.collection

import org.usvm.StepScope
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.writeArrayIndex
import org.usvm.api.writeArrayLength
import org.usvm.memory.mapWithStaticAsConcrete
import org.usvm.uctx
import org.usvm.withSizeSort

object ListCollectionApi {
    fun <ListType> UState<ListType, *, *, *, *, *>.mkSymbolicList(
        listType: ListType,
    ): UHeapRef = with(memory.ctx) {
        val ref = memory.allocConcrete(listType)
        memory.writeArrayLength(ref, mkSizeExpr(0), listType)
        ref
    }

    /**
     * List size may be incorrect for input lists.
     * Use [ensureListSizeCorrect] to guarantee that list size is correct.
     * */
    fun <ListType, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListSize(
        listRef: UHeapRef,
        listType: ListType,
    ): UExpr<USizeSort> = memory.readArrayLength(listRef, listType)

    fun <ListType, USizeSort : USort, State : UState<ListType, *, *, *, *, State>> StepScope<State, ListType, *>.ensureListSizeCorrect(
        listRef: UHeapRef,
        listType: ListType,
    ): Unit? {
        listRef.mapWithStaticAsConcrete(
            concreteMapper = {
                // Concrete list size is always correct
                it
            },
            symbolicMapper = { symbolicListRef ->
                val length = calcOnState { memory.readArrayLength<ListType, USizeSort>(symbolicListRef, listType) }
                with(length.uctx.withSizeSort<USizeSort>()) {
                    val boundConstraint = mkSizeGeExpr(length, mkSizeExpr(0))
                    // List size must be correct regardless of guard
                    assert(boundConstraint) ?: return null
                }
                symbolicListRef
            }
        )
        return Unit
    }

    fun <ListType, Sort : USort, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListGet(
        listRef: UHeapRef,
        index: UExpr<USizeSort>,
        listType: ListType,
        sort: Sort,
    ): UExpr<Sort> = memory.readArrayIndex(listRef, index, listType, sort)

    fun <ListType, Sort : USort, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListAdd(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        value: UExpr<Sort>,
    ) {
        val size = symbolicListSize<ListType, USizeSort>(listRef, listType)

        with(memory.ctx.withSizeSort<USizeSort>()) {
            memory.writeArrayIndex(listRef, size, listType, sort, value, guard = trueExpr)
            val updatedSize = mkSizeAddExpr(size, mkSizeExpr(1))
            memory.writeArrayLength(listRef, updatedSize, listType)
        }
    }

    fun <ListType, Sort : USort, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListSet(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: UExpr<USizeSort>,
        value: UExpr<Sort>,
    ) {
        memory.writeArrayIndex(listRef, index, listType, sort, value, guard = memory.ctx.trueExpr)
    }

    fun <ListType, Sort : USort, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListInsert(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: UExpr<USizeSort>,
        value: UExpr<Sort>,
    ) = with(memory.ctx.withSizeSort<USizeSort>()) {
        val currentSize = symbolicListSize<ListType, USizeSort>(listRef, listType)

        val srcIndex = index
        val indexAfterInsert = mkSizeAddExpr(index, mkSizeExpr(1))
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

        val updatedSize = mkSizeAddExpr(currentSize, mkSizeExpr(1))
        memory.writeArrayLength(listRef, updatedSize, listType)
    }

    fun <ListType, Sort : USort, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListRemove(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: UExpr<USizeSort>,
    ) = with(memory.ctx.withSizeSort<USizeSort>()) {
        val currentSize = symbolicListSize<ListType, USizeSort>(listRef, listType)

        val firstIndexAfterRemove = mkSizeAddExpr(index, mkSizeExpr(1))
        val lastIndexAfterRemove = mkSizeSubExpr(currentSize, mkSizeExpr(2))

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

        val updatedSize = mkSizeSubExpr(currentSize, mkSizeExpr(1))
        memory.writeArrayLength(listRef, updatedSize, listType)
    }

    fun <ListType, Sort : USort, USizeSort : USort> UState<ListType, *, *, *, *, *>.symbolicListCopyRange(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        srcFrom: UExpr<USizeSort>,
        dstFrom: UExpr<USizeSort>,
        length: UExpr<USizeSort>,
    ) {
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
