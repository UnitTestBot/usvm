package org.usvm.intrinsics.collections

import org.usvm.*
import org.usvm.memory.USymbolicIndexMapDescriptor
import org.usvm.memory.USymbolicMapDescriptor

object SymbolicListIntrinsics : SymbolicCollectionIntrinsics {
    object SymbolicListMarker : USymbolicMapDescriptor.SymbolicMapInfo {
        override fun toString(): String = "List"
    }

    fun UState<*, *, *, *>.mkSymbolicList(
        elementSort: USort
    ): UHeapRef = mkSymbolicCollection(elementSort)

    fun UState<*, *, *, *>.symbolicListSize(
        listRef: UHeapRef,
        elementSort: USort
    ): USizeExpr = symbolicCollectionSize(listRef, elementSort)

    fun UState<*, *, *, *>.symbolicListGet(
        listRef: UHeapRef,
        index: USizeExpr,
        elementSort: USort
    ): UExpr<out USort> = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)
        readSymbolicMap(descriptor, listRef, index)
    }

    fun UState<*, *, *, *>.symbolicListAdd(
        listRef: UHeapRef,
        elementSort: USort,
        value: UExpr<out USort>
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)

        val size = readCollectionSize(listRef, elementSort)
        writeSymbolicMap(descriptor, listRef, size, value, guard = ctx.trueExpr)

        updateCollectionSize(listRef, elementSort, ctx.trueExpr) { oldSize ->
            ctx.mkBvAddExpr(oldSize, ctx.mkBv(1))
        }
    }

    fun UState<*, *, *, *>.symbolicListSet(
        listRef: UHeapRef,
        elementSort: USort,
        index: USizeExpr,
        value: UExpr<out USort>
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)
        writeSymbolicMap(descriptor, listRef, index, value, guard = ctx.trueExpr)
    }

    fun UState<*, *, *, *>.symbolicListInsert(
        listRef: UHeapRef,
        elementSort: USort,
        index: USizeExpr,
        value: UExpr<out USort>
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)

        val currentSize = readCollectionSize(listRef, elementSort)
        val srcIndex = ctx.mkBvAddExpr(index, ctx.mkBv(2))
        val indexAfterInsert = ctx.mkBvAddExpr(index, ctx.mkBv(1))
        val lastIndexAfterInsert = ctx.mkBvSubExpr(currentSize, ctx.mkBv(1))

        copySymbolicMapIndexRange(
            descriptor = descriptor,
            srcRef = listRef,
            dstRef = listRef,
            fromSrcKey = srcIndex,
            fromDstKey = indexAfterInsert,
            toDstKey = lastIndexAfterInsert,
            guard = ctx.trueExpr
        )

        writeSymbolicMap(descriptor, listRef, index, value, guard = ctx.trueExpr)
        updateCollectionSize(listRef, elementSort, ctx.trueExpr) { oldSize ->
            ctx.mkBvAddExpr(oldSize, ctx.mkBv(1))
        }
    }

    fun UState<*, *, *, *>.symbolicListRemove(
        listRef: UHeapRef,
        elementSort: USort,
        index: USizeExpr
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)

        val currentSize = readCollectionSize(listRef, elementSort)
        val firstIndexAfterRemove = ctx.mkBvSubExpr(index, ctx.mkBv(1))
        val lastIndexAfterRemove = ctx.mkBvSubExpr(currentSize, ctx.mkBv(2))

        copySymbolicMapIndexRange(
            descriptor = descriptor,
            srcRef = listRef,
            dstRef = listRef,
            fromSrcKey = firstIndexAfterRemove,
            fromDstKey = index,
            toDstKey = lastIndexAfterRemove,
            guard = ctx.trueExpr
        )

        updateCollectionSize(listRef, elementSort, ctx.trueExpr) { oldSize ->
            ctx.mkBvSubExpr(oldSize, ctx.mkBv(1))
        }
    }

    fun UState<*, *, *, *>.symbolicListCopyRange(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        elementSort: USort,
        srcFrom: USizeExpr,
        dstFrom: USizeExpr,
        length: USizeExpr
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)

        val dstTo = ctx.mkBvAddExpr(dstFrom, length)

        copySymbolicMapIndexRange(
            descriptor = descriptor,
            srcRef = srcRef,
            dstRef = dstRef,
            fromSrcKey = srcFrom,
            fromDstKey = dstFrom,
            toDstKey = dstTo,
            guard = ctx.trueExpr
        )
    }

    override fun UState<*, *, *, *>.symbolicCollectionSizeDescriptor(
        collection: UHeapRef,
        elementSort: USort
    ): USymbolicMapDescriptor<*, *, *> = ctx.listDescriptor(elementSort)

    private fun UContext.listDescriptor(valueSort: USort) = USymbolicIndexMapDescriptor(
        valueSort = valueSort,
        defaultValue = valueSort.sampleUValue(),
        info = SymbolicListMarker
    )
}
