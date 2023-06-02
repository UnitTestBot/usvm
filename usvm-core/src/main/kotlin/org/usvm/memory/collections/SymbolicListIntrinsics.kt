package org.usvm.memory.collections

import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.memory.USymbolicIndexMapDescriptor
import org.usvm.sampleUValue

object SymbolicListIntrinsics {
    object SymbolicListMarker

    fun UState<*, *, *, *>.mkSymbolicList(
        elementSort: USort
    ): UHeapRef = with(memory.heap) {
        allocate().also { ref ->
            val descriptor = ctx.listDescriptor(elementSort)
            writeSymbolicMapLength(descriptor, ref, ctx.mkBv(0))
        }
    }

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
        writeSymbolicMapLength(descriptor, listRef, newSize)
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

        copySymbolicMap(
            descriptor = descriptor,
            srcRef = listRef,
            dstRef = listRef,
            fromSrcKey = index,
            fromDstKey = newIndex,
            toDstKey = newSize,
            guard = ctx.trueExpr
        )

        writeSymbolicMap(descriptor, listRef, index, value, guard = ctx.trueExpr)
        writeSymbolicMapLength(descriptor, listRef, newSize)
    }

    fun UState<*, *, *, *>.symbolicListCopy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        elementSort: USort,
        srcFrom: USizeExpr,
        dstFrom: USizeExpr,
        length: USizeExpr
    ) = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)

        val dstTo = ctx.mkBvAddExpr(dstFrom, length)

        copySymbolicMap(
            descriptor = descriptor,
            srcRef = srcRef,
            dstRef = dstRef,
            fromSrcKey = srcFrom,
            fromDstKey = dstFrom,
            toDstKey = dstTo,
            guard = ctx.trueExpr
        )
    }

    fun UState<*, *, *, *>.symbolicListSize(
        listRef: UHeapRef,
        elementSort: USort
    ): USizeExpr = with(memory.heap) {
        val descriptor = ctx.listDescriptor(elementSort)
        return readSymbolicMapLength(descriptor, listRef)
    }

    private fun UContext.listDescriptor(valueSort: USort) = USymbolicIndexMapDescriptor(
        valueSort = valueSort,
        defaultValue = valueSort.sampleUValue(),
        info = SymbolicListMarker
    )
}
