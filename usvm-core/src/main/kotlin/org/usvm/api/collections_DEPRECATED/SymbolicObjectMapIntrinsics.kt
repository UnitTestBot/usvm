//package org.usvm.api.collections
//
//import io.ksmt.utils.asExpr
//import io.ksmt.utils.mkFreshConst
//import org.usvm.*
//import org.usvm.memory.USymbolicMapDescriptor
//import org.usvm.memory.USymbolicObjectReferenceMapDescriptor
//
//object SymbolicObjectMapIntrinsics : SymbolicCollectionIntrinsics {
//    data class SymbolicObjectMapValueMarker(
//        val valueSort: USort
//    ) : USymbolicMapDescriptor.SymbolicMapInfo {
//        override fun toString(): String = "Map<$valueSort>Value"
//    }
//
//    data class SymbolicObjectMapContainsMarker(
//        val valueSort: USort
//    ) : USymbolicMapDescriptor.SymbolicMapInfo {
//        override fun toString(): String = "Map<$valueSort>Contains"
//    }
//
//    fun UState<*, *, *, *>.mkSymbolicObjectMap(elementSort: USort): UHeapRef =
//        mkSymbolicCollection(elementSort)
//
//    // todo: input map size can be inconsistent with contains
//    fun UState<*, *, *, *>.symbolicObjectMapSize(mapRef: UHeapRef, elementSort: USort): USizeExpr =
//        symbolicCollectionSize(mapRef, elementSort)
//
//    fun UState<*, *, *, *>.symbolicObjectMapGet(
//        mapRef: UHeapRef,
//        key: UHeapRef,
//        elementSort: USort
//    ): UExpr<out USort> = with(memory.heap) {
//        val descriptor = ctx.valueDescriptor(elementSort)
//        val keyId = mkKeyId(key)
//        readSymbolicMap(descriptor, mapRef, keyId)
//    }
//
//    fun UState<*, *, *, *>.symbolicObjectMapContains(
//        mapRef: UHeapRef,
//        key: UHeapRef,
//        elementSort: USort
//    ): UBoolExpr = with(memory.heap) {
//        val descriptor = ctx.containsDescriptor(elementSort)
//        val keyId = mkKeyId(key)
//        readSymbolicMap(descriptor, mapRef, keyId).asExpr(ctx.boolSort)
//    }
//
//    fun UState<*, *, *, *>.symbolicObjectMapPut(
//        mapRef: UHeapRef,
//        key: UHeapRef,
//        elementSort: USort,
//        value: UExpr<out USort>
//    ): Unit = with(memory.heap) {
//        val valueDescriptor = ctx.valueDescriptor(elementSort)
//        val containsDescriptor = ctx.containsDescriptor(elementSort)
//
//        val keyId = mkKeyId(key)
//
//        val keyIsInMap = readSymbolicMap(containsDescriptor, mapRef, keyId).asExpr(ctx.boolSort)
//        val keyIsNew = ctx.mkNot(keyIsInMap)
//
//        writeSymbolicMap(valueDescriptor, mapRef, keyId, value, guard = ctx.trueExpr)
//        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.trueExpr, guard = ctx.trueExpr)
//        updateCollectionSize(mapRef, elementSort, keyIsNew) { ctx.mkBvAddExpr(it, ctx.mkBv(1)) }
//    }
//
//    fun UState<*, *, *, *>.symbolicObjectMapRemove(
//        mapRef: UHeapRef,
//        key: UHeapRef,
//        elementSort: USort
//    ): Unit = with(memory.heap) {
//        val containsDescriptor = ctx.containsDescriptor(elementSort)
//
//        val keyId = mkKeyId(key)
//
//        val keyIsInMap = readSymbolicMap(containsDescriptor, mapRef, keyId).asExpr(ctx.boolSort)
//
//        // todo: skip values update?
//        writeSymbolicMap(containsDescriptor, mapRef, keyId, value = ctx.falseExpr, guard = ctx.trueExpr)
//        updateCollectionSize(mapRef, elementSort, keyIsInMap) { ctx.mkBvSubExpr(it, ctx.mkBv(1)) }
//    }
//
//    fun UState<*, *, *, *>.symbolicObjectMapMergeInto(
//        dstRef: UHeapRef,
//        srcRef: UHeapRef,
//        elementSort: USort
//    ): Unit = with(memory.heap) {
//        val valueDescriptor = ctx.valueDescriptor(elementSort)
//        val containsDescriptor = ctx.containsDescriptor(elementSort)
//
//        mergeSymbolicMap(
//            descriptor = valueDescriptor,
//            keyContainsDescriptor = containsDescriptor,
//            srcRef = srcRef,
//            dstRef = dstRef,
//            guard = ctx.trueExpr
//        )
//
//        mergeSymbolicMap(
//            descriptor = containsDescriptor,
//            keyContainsDescriptor = containsDescriptor,
//            srcRef = srcRef,
//            dstRef = dstRef,
//            guard = ctx.trueExpr
//        )
//
//        // todo: precise map size approximation?
//        val mergedMapSize = ctx.sizeSort.mkFreshConst("mergedMapSize")
//        val srcMapSize = symbolicCollectionSize(srcRef, elementSort)
//        val dstMapSize = symbolicCollectionSize(dstRef, elementSort)
//        val sizeLowerBound = ctx.mkIte(ctx.mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
//        val sizeUpperBound = ctx.mkBvAddExpr(srcMapSize, dstMapSize)
//        pathConstraints += ctx.mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeLowerBound)
//        pathConstraints += ctx.mkBvSignedGreaterOrEqualExpr(mergedMapSize, sizeUpperBound)
//        updateCollectionSize(dstRef, elementSort, ctx.trueExpr) { mergedMapSize }
//    }
//
//    override fun UState<*, *, *, *>.symbolicCollectionSizeDescriptor(
//        collection: UHeapRef,
//        elementSort: USort
//    ): USymbolicMapDescriptor<*, *, *> = ctx.containsDescriptor(elementSort)
//
//    // todo: use identity equality instead of reference equality
//    private fun mkKeyId(key: UHeapRef): UHeapRef = key
//
//    private fun UContext.valueDescriptor(valueSort: USort) = USymbolicObjectReferenceMapDescriptor(
//        valueSort = valueSort,
//        defaultValue = valueSort.sampleUValue(),
//        info = SymbolicObjectMapValueMarker(valueSort)
//    )
//
//    private fun UContext.containsDescriptor(valueSort: USort) = USymbolicObjectReferenceMapDescriptor(
//        valueSort = boolSort,
//        defaultValue = falseExpr,
//        info = SymbolicObjectMapContainsMarker(valueSort)
//    )
//}
