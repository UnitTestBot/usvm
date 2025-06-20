package org.usvm.api.collection

import org.usvm.StepScope
import org.usvm.UContext
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
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeGtExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
import org.usvm.utils.logAssertFailure

object ListCollectionApi {
    fun <ListType, USizeSort : USort, Ctx: UContext<USizeSort>> UState<ListType, *, *, Ctx, *, *>.mkSymbolicList(
        listType: ListType,
    ): UHeapRef = with(ctx) {
        val ref = memory.allocConcrete(listType)
        memory.writeArrayLength(ref, mkSizeExpr(0), listType, sizeSort)
        ref
    }

    /**
     * List size may be incorrect for input lists.
     * Use [ensureListSizeCorrect] to guarantee that list size is correct.
     * */
    fun <ListType, USizeSort : USort, Ctx: UContext<USizeSort>> UState<ListType, *, *, Ctx, *, *>.symbolicListSize(
        listRef: UHeapRef,
        listType: ListType,
    ): UExpr<USizeSort> = memory.readArrayLength(listRef, listType, ctx.sizeSort)

    fun <ListType, USizeSort : USort, State, Ctx> StepScope<State, ListType, *, *>.ensureListSizeCorrect(
        listRef: UHeapRef,
        listType: ListType,
    ): Unit? where State : UState<ListType, *, *, Ctx, *, State>, Ctx : UContext<USizeSort> {
        listRef.mapWithStaticAsConcrete(
            concreteMapper = {
                // Concrete list size is always correct
                it
            },
            symbolicMapper = { symbolicListRef ->
                val length = calcOnState { memory.readArrayLength(symbolicListRef, listType, ctx.sizeSort) }
                val ctx = calcOnState { ctx }
                with(ctx) {
                    val boundConstraint = mkSizeGeExpr(length, mkSizeExpr(0))
                    // List size must be correct regardless of guard
                    assert(boundConstraint)
                        .logAssertFailure { "Constraint violation: SymbolicList size correctness constraint" }
                        ?: return null
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

    fun <ListType, Sort : USort, USizeSort : USort, Ctx: UContext<USizeSort>> UState<ListType, *, *, Ctx, *, *>.symbolicListAdd(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        value: UExpr<Sort>,
    ) {
        val size = symbolicListSize(listRef, listType)

        with(ctx) {
            memory.writeArrayIndex(listRef, size, listType, sort, value, guard = trueExpr)
            val updatedSize = mkSizeAddExpr(size, mkSizeExpr(1))
            memory.writeArrayLength(listRef, updatedSize, listType, sizeSort)
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

    fun <ListType, Sort : USort, USizeSort : USort, Ctx: UContext<USizeSort>> UState<ListType, *, *, Ctx, *, *>.symbolicListInsert(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: UExpr<USizeSort>,
        value: UExpr<Sort>,
    ) = with(ctx) {
        val currentSize = symbolicListSize(listRef, listType)

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
        memory.writeArrayLength(listRef, updatedSize, listType, sizeSort)
    }

    fun <ListType, Sort : USort, USizeSort : USort, Ctx: UContext<USizeSort>> UState<ListType, *, *, Ctx, *, *>.symbolicListRemove(
        listRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        index: UExpr<USizeSort>,
    ) = with(ctx) {
        val currentSize = symbolicListSize(listRef, listType)

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
        memory.writeArrayLength(listRef, updatedSize, listType, sizeSort)
    }

    private fun <USizeSort : USort, Ctx: UContext<USizeSort>> Ctx.max(
        first: UExpr<USizeSort>,
        second: UExpr<USizeSort>
    ): UExpr<USizeSort> = mkIte(mkSizeGtExpr(first, second), first, second)

    fun <ListType, Sort : USort, USizeSort : USort, Ctx: UContext<USizeSort>> UState<ListType, *, *, Ctx, *, *>.symbolicListCopyRange(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        listType: ListType,
        sort: Sort,
        srcFrom: UExpr<USizeSort>,
        dstFrom: UExpr<USizeSort>,
        length: UExpr<USizeSort>,
    ) {
        // Copying contents
        memory.memcpy(
            srcRef = srcRef,
            dstRef = dstRef,
            type = listType,
            elementSort = sort,
            fromSrc = srcFrom,
            fromDst = dstFrom,
            length = length
        )

        // Modifying destination length
        val dstLength = symbolicListSize(dstRef, listType)
        val copyLength = ctx.mkSizeAddExpr(dstFrom, length)
        val resultDstLength = ctx.max(dstLength, copyLength)
        memory.writeArrayLength(dstRef, resultDstLength, listType, ctx.sizeSort)
    }
}
