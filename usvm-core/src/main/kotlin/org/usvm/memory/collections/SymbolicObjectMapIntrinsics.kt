package org.usvm.memory.collections

import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.memory.USymbolicObjectReferenceMapDescriptor
import org.usvm.sampleUValue

object SymbolicObjectMapIntrinsics {
    object SymbolicObjectMapValueMarker {
        override fun toString(): String = "MapValue"
    }

    object SymbolicObjectMapContainsMarker {
        override fun toString(): String = "MapContains"
    }

    fun UState<*, *, *, *>.mkSymbolicObjectMap(
        elementSort: USort
    ): UHeapRef = with(memory.heap) {
        allocate().also { ref ->
            val valueDescriptor = ctx.valueDescriptor(elementSort)
            writeSymbolicMapLength(valueDescriptor, ref, ctx.mkBv(0))
        }
    }

    fun UState<*, *, *, *>.symbolicObjectMapGet(
        mapRef: UHeapRef,
        key: UHeapRef,
        elementSort: USort
    ): UExpr<out USort> = with(memory.heap) {
        val descriptor = ctx.valueDescriptor(elementSort)
        val keyId = mkKeyId(key)
        readSymbolicMap(descriptor, mapRef, keyId)
    }

    fun UState<*, *, *, *>.symbolicObjectMapContains(
        mapRef: UHeapRef,
        key: UHeapRef
    ): UBoolExpr = with(memory.heap) {
        val descriptor = ctx.containsDescriptor()
        val keyId = mkKeyId(key)
        readSymbolicMap(descriptor, mapRef, keyId).asExpr(ctx.boolSort)
    }

    fun UState<*, *, *, *>.symbolicObjectMapPut(
        mapRef: UHeapRef,
        key: UHeapRef,
        elementSort: USort,
        value: UExpr<out USort>
    ): Unit = with(memory.heap) {
        val valueDescriptor = ctx.valueDescriptor(elementSort)
        val containsDescriptor = ctx.containsDescriptor()

        val keyId = mkKeyId(key)

        val oldSize = readSymbolicMapLength(valueDescriptor, mapRef)
        val newSize = ctx.mkBvAddExpr(oldSize, ctx.mkBv(1))

        writeSymbolicMap(valueDescriptor, mapRef, keyId, value, guard = ctx.trueExpr)
        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.trueExpr, guard = ctx.trueExpr)
        writeSymbolicMapLength(valueDescriptor, mapRef, newSize)
    }

    fun UState<*, *, *, *>.symbolicObjectMapSize(
        mapRef: UHeapRef,
        elementSort: USort
    ): USizeExpr = with(memory.heap) {
        val valueDescriptor = ctx.valueDescriptor(elementSort)
        readSymbolicMapLength(valueDescriptor, mapRef)
    }

    fun UState<*, *, *, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef,
        elementSort: USort
    ): Unit = with(memory.heap) {
        val valueDescriptor = ctx.valueDescriptor(elementSort)
        val containsDescriptor = ctx.containsDescriptor()

        val keyId = mkKeyId(key)

        val oldSize = readSymbolicMapLength(valueDescriptor, mapRef)
        val newSize = ctx.mkBvSubExpr(oldSize, ctx.mkBv(1))

        // todo: skip values update?
        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.falseExpr, guard = ctx.trueExpr)
        writeSymbolicMapLength(valueDescriptor, mapRef, newSize)
    }

    // todo: use identity equality instead of reference equality
    private fun mkKeyId(key: UHeapRef): UHeapRef = key

    private fun UContext.valueDescriptor(valueSort: USort) = USymbolicObjectReferenceMapDescriptor(
        valueSort = valueSort,
        defaultValue = valueSort.sampleUValue(),
        info = SymbolicObjectMapValueMarker
    )

    private fun UContext.containsDescriptor() = USymbolicObjectReferenceMapDescriptor(
        valueSort = boolSort,
        defaultValue = falseExpr,
        info = SymbolicObjectMapContainsMarker
    )
}
