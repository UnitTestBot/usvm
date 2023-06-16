package org.usvm.intrinsics.collections

import io.ksmt.utils.asExpr
import io.ksmt.utils.mkFreshConst
import org.usvm.*
import org.usvm.memory.USymbolicHeap
import org.usvm.memory.USymbolicMapDescriptor
import org.usvm.memory.USymbolicObjectReferenceMapDescriptor

object SymbolicObjectMapIntrinsics {
    object SymbolicObjectMapValueMarker: USymbolicMapDescriptor.SymbolicMapInfo {
        override fun toString(): String = "MapValue"
    }

    object SymbolicObjectMapContainsMarker: USymbolicMapDescriptor.SymbolicMapInfo {
        override fun toString(): String = "MapContains"
    }

    fun UState<*, *, *, *>.mkSymbolicObjectMap(): UHeapRef = with(memory.heap) {
        allocate().also { ref ->
            updateMapSize(ref, ctx.trueExpr) { _ -> ctx.mkBv(0) }
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

        val keyIsInMap = readSymbolicMap(containsDescriptor, mapRef, keyId).asExpr(ctx.boolSort)
        val keyIsNew = ctx.mkNot(keyIsInMap)

        writeSymbolicMap(valueDescriptor, mapRef, keyId, value, guard = ctx.trueExpr)
        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.trueExpr, guard = ctx.trueExpr)
        updateMapSize(mapRef, keyIsNew) { oldSize -> ctx.mkBvAddExpr(oldSize, ctx.mkBv(1)) }
    }

    fun UState<*, *, *, *>.symbolicObjectMapSize(
        mapRef: UHeapRef
    ): USizeExpr = with(memory.heap) {
        readMapSize(mapRef)
    }

    fun UState<*, *, *, *>.symbolicObjectMapRemove(
        mapRef: UHeapRef,
        key: UHeapRef
    ): Unit = with(memory.heap) {
        val containsDescriptor = ctx.containsDescriptor()

        val keyId = mkKeyId(key)

        val keyIsInMap = readSymbolicMap(containsDescriptor, mapRef, keyId).asExpr(ctx.boolSort)

        // todo: skip values update?
        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.falseExpr, guard = ctx.trueExpr)
        updateMapSize(mapRef, keyIsInMap) { oldSize -> ctx.mkBvSubExpr(oldSize, ctx.mkBv(1)) }
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
        val srcMapSize = symbolicObjectMapSize(srcRef)
        val dstMapSize = symbolicObjectMapSize(dstRef)
        val sizeLowerBound = ctx.mkIte(ctx.mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
        val sizeUpperBound = ctx.mkBvAddExpr(srcMapSize, dstMapSize)
        pathConstraints += ctx.mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeLowerBound)
        pathConstraints += ctx.mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeUpperBound)
        updateMapSize(dstRef, ctx.trueExpr) { _ -> mergedMapSize }
    }

    private fun USymbolicHeap<*, *>.readMapSize(
        map: UHeapRef,
    ): USizeExpr {
        val descriptor = map.uctx.containsDescriptor()
        val size = readSymbolicMapLength(descriptor, map)

        return if (map is UConcreteHeapRef) {
            size
        } else {
            // todo: input map size can be inconsistent with contains
            size.uctx.ensureAtLeasZero(size)
        }
    }

    private inline fun USymbolicHeap<*, *>.updateMapSize(
        map: UHeapRef,
        guard: UBoolExpr,
        update: (USizeExpr) -> USizeExpr
    ) {
        val descriptor = map.uctx.containsDescriptor()
        val oldSize = readMapSize(map)
        val updatedSize = update(oldSize)
        writeSymbolicMapLength(descriptor, map, updatedSize, guard)
    }

    private fun UContext.ensureAtLeasZero(expr: USizeExpr): USizeExpr =
        mkIte(mkBvSignedGreaterOrEqualExpr(expr, mkSizeExpr(0)), expr, mkSizeExpr(0))

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
