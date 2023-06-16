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

        val oldSize = readSymbolicMapLength(descriptor, listRef)
        val newSize = ctx.mkBvAddExpr(oldSize, ctx.mkBv(1))

        writeSymbolicMap(descriptor, listRef, oldSize, value, guard = ctx.trueExpr)
        writeSymbolicMapLength(descriptor, listRef, newSize, guard = ctx.trueExpr)
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

        val oldSize = readSymbolicMapLength(descriptor, listRef)
        val newIndex = ctx.mkBvAddExpr(index, ctx.mkBv(1))
        val newSize = ctx.mkBvAddExpr(oldSize, ctx.mkBv(1))

        copySymbolicMapIndexRange(
            descriptor = descriptor,
            srcRef = listRef,
            dstRef = listRef,
            fromSrcKey = index,
            fromDstKey = newIndex,
            toDstKey = newSize,
            guard = ctx.trueExpr
        )

        writeSymbolicMap(descriptor, listRef, index, value, guard = ctx.trueExpr)
        writeSymbolicMapLength(descriptor, listRef, newSize, guard = ctx.trueExpr)
    }

    fun UState<*, *, *, *>.symbolicListRemove(
        listRef: UHeapRef,
        elementSort: USort,
        index: USizeExpr
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)

        val oldSize = readSymbolicMapLength(descriptor, listRef)
        val newIndex = ctx.mkBvSubExpr(index, ctx.mkBv(1))
        val newSize = ctx.mkBvSubExpr(oldSize, ctx.mkBv(1))

        copySymbolicMapIndexRange(
            descriptor = descriptor,
            srcRef = listRef,
            dstRef = listRef,
            fromSrcKey = index,
            fromDstKey = newIndex,
            toDstKey = newSize,
            guard = ctx.trueExpr
        )

        writeSymbolicMapLength(descriptor, listRef, newSize, guard = ctx.trueExpr)
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
