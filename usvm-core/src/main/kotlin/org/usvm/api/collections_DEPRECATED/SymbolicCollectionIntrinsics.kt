//package org.usvm.api.collections
//
//import org.usvm.*
//import org.usvm.memory.USymbolicMapDescriptor
//
//interface SymbolicCollectionIntrinsics {
//
//    fun UState<*, *, *, *>.mkSymbolicCollection(elementSort: USort): UHeapRef = with(memory.heap) {
//        allocate().also { ref ->
//            updateCollectionSize(ref, elementSort, ctx.trueExpr) { ctx.mkBv(0) }
//        }
//    }
//
//    fun UState<*, *, *, *>.symbolicCollectionSize(
//        collection: UHeapRef,
//        elementSort: USort
//    ): USizeExpr = readCollectionSize(collection, elementSort)
//
//    fun UState<*, *, *, *>.symbolicCollectionSizeDescriptor(
//        collection: UHeapRef,
//        elementSort: USort
//    ): USymbolicMapDescriptor<*, *, *>
//
//    fun UState<*, *, *, *>.readCollectionSize(
//        collection: UHeapRef,
//        elementSort: USort
//    ): USizeExpr = readCollectionSize(collection, symbolicCollectionSizeDescriptor(collection, elementSort))
//
//    fun UState<*, *, *, *>.readCollectionSize(
//        collection: UHeapRef,
//        descriptor: USymbolicMapDescriptor<*, *, *>
//    ): USizeExpr {
//        val size = memory.heap.readSymbolicMapLength(descriptor, collection)
//
//        return if (collection is UConcreteHeapRef) {
//            size
//        } else {
//            ctx.ensureAtLeasZero(size)
//        }
//    }
//
//    fun UState<*, *, *, *>.updateCollectionSize(
//        collection: UHeapRef,
//        elementSort: USort,
//        guard: UBoolExpr,
//        update: (USizeExpr) -> USizeExpr
//    ) = updateCollectionSize(collection, symbolicCollectionSizeDescriptor(collection, elementSort), guard, update)
//
//    fun UState<*, *, *, *>.updateCollectionSize(
//        collection: UHeapRef,
//        descriptor: USymbolicMapDescriptor<*, *, *>,
//        guard: UBoolExpr,
//        update: (USizeExpr) -> USizeExpr
//    ) {
//        val oldSize = readCollectionSize(collection, descriptor)
//        val updatedSize = update(oldSize)
//        memory.heap.writeSymbolicMapLength(descriptor, collection, updatedSize, guard)
//    }
//
//    private fun UContext.ensureAtLeasZero(expr: USizeExpr): USizeExpr =
//        mkIte(mkBvSignedGreaterOrEqualExpr(expr, mkSizeExpr(0)), expr, mkSizeExpr(0))
//}
