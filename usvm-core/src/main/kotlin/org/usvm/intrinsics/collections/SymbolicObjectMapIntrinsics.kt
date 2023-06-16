package org.usvm.intrinsics.collections

import io.ksmt.utils.asExpr
import io.ksmt.utils.mkFreshConst
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.memory.USymbolicMapDescriptor
import org.usvm.memory.USymbolicObjectReferenceMapDescriptor
import org.usvm.sampleUValue

object SymbolicObjectMapIntrinsics {
    object SymbolicObjectMapValueMarker: USymbolicMapDescriptor.SymbolicMapInfo {
        override fun toString(): String = "MapValue"
    }

    object SymbolicObjectMapContainsMarker: USymbolicMapDescriptor.SymbolicMapInfo {
        override fun toString(): String = "MapContains"
    }

    fun UState<*, *, *, *>.mkSymbolicObjectMap(
        elementSort: USort
    ): UHeapRef = with(memory.heap) {
        allocate().also { ref ->
            val valueDescriptor = ctx.valueDescriptor(elementSort)
            writeSymbolicMapLength(valueDescriptor, ref, size = ctx.mkBv(0), guard = ctx.trueExpr)
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
        val increasedSize = ctx.mkBvAddExpr(oldSize, ctx.mkBv(1))
        val keyIsInMap = readSymbolicMap(containsDescriptor, mapRef, keyId).asExpr(ctx.boolSort)

        writeSymbolicMap(valueDescriptor, mapRef, keyId, value, guard = ctx.trueExpr)
        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.trueExpr, guard = ctx.trueExpr)
        writeSymbolicMapLength(valueDescriptor, mapRef, increasedSize, guard = keyIsInMap)
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
        val decreasedSize = ctx.mkBvSubExpr(oldSize, ctx.mkBv(1))
        val keyIsInMap = readSymbolicMap(containsDescriptor, mapRef, keyId).asExpr(ctx.boolSort)

        // todo: skip values update?
        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.falseExpr, guard = ctx.trueExpr)
        writeSymbolicMapLength(valueDescriptor, mapRef, decreasedSize, guard = keyIsInMap)
    }

    fun UState<*, *, *, *>.symbolicObjectMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        elementSort: USort
    ): Unit = with(memory.heap) {
        val valueDescriptor = ctx.valueDescriptor(elementSort)
        val containsDescriptor = ctx.containsDescriptor()

        mergeSymbolicMap(
            descriptor = valueDescriptor,
            keyContainsDescriptor = containsDescriptor,
            srcRef = srcRef,
            dstRef = dstRef,
            guard = ctx.trueExpr
        )

        mergeSymbolicMap(
            descriptor = containsDescriptor,
            keyContainsDescriptor = containsDescriptor,
            srcRef = srcRef,
            dstRef = dstRef,
            guard = ctx.trueExpr
        )

        // todo: precise map size approximation?
        val mergedMapSize = ctx.sizeSort.mkFreshConst("mergedMapSize")
        val srcMapSize = readSymbolicMapLength(valueDescriptor, srcRef)
        val dstMapSize = readSymbolicMapLength(valueDescriptor, dstRef)
        val sizeLowerBound = ctx.mkIte(ctx.mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
        val sizeUpperBound = ctx.mkBvAddExpr(srcMapSize, dstMapSize)
        pathConstraints += ctx.mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeLowerBound)
        pathConstraints += ctx.mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeUpperBound)
        writeSymbolicMapLength(valueDescriptor, dstRef, mergedMapSize, guard = ctx.trueExpr)
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
