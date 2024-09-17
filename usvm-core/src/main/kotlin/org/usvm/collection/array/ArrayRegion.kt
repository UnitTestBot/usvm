package org.usvm.collection.array

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.getOrPut
import org.usvm.uctx
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.mapWithStaticAsSymbolic

data class UArrayIndexLValue<ArrayType, Sort : USort, USizeSort : USort>(
    override val sort: Sort,
    val ref: UHeapRef,
    val index: UExpr<USizeSort>,
    val arrayType: ArrayType,
) : ULValue<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    override val memoryRegionId: UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> =
        UArrayRegionId(arrayType, sort)

    override val key: UArrayIndexLValue<ArrayType, Sort, USizeSort>
        get() = this
}

data class UArrayRegionId<ArrayType, Sort : USort, USizeSort : USort>(val arrayType: ArrayType, override val sort: Sort) :
    UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> =
        UArrayMemoryRegion()
}

typealias UAllocatedArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>
typealias UInputArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>

interface UArrayRegion<ArrayType, Sort : USort, USizeSort : USort> : UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: UExpr<USizeSort>,
        fromDstIdx: UExpr<USizeSort>,
        toDstIdx: UExpr<USizeSort>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UArrayRegion<ArrayType, Sort, USizeSort>

    fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<UExpr<USizeSort>, UExpr<Sort>>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UArrayRegion<ArrayType, Sort, USizeSort>
}

internal class UArrayMemoryRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private var allocatedArrays: UPersistentHashMap<UConcreteHeapAddress, UAllocatedArray<ArrayType, Sort, USizeSort>> = persistentHashMapOf(),
    private var inputArray: UInputArray<ArrayType, Sort, USizeSort>? = null,
) : UArrayRegion<ArrayType, Sort, USizeSort> {

    private fun getAllocatedArray(
        arrayType: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress,
    ): UAllocatedArray<ArrayType, Sort, USizeSort> {
        val (updatedArrays, collection) = allocatedArrays.getOrPut(address, sort.uctx.defaultOwnership) {
            UAllocatedArrayId<_, _, USizeSort>(arrayType, sort, address).emptyRegion()
        }
        allocatedArrays = updatedArrays
        return collection
    }

    private fun updateAllocatedArray(
        ref: UConcreteHeapAddress,
        updated: UAllocatedArray<ArrayType, Sort, USizeSort>,
        ownership: MutabilityOwnership,
    ) = UArrayMemoryRegion(allocatedArrays.put(ref, updated, ownership), inputArray)

    private fun getInputArray(arrayType: ArrayType, sort: Sort): UInputArray<ArrayType, Sort, USizeSort> {
        if (inputArray == null)
            inputArray = UInputArrayId<_, _, USizeSort>(arrayType, sort).emptyRegion()
        return inputArray!!
    }

    private fun updateInput(updated: UInputArray<ArrayType, Sort, USizeSort>) =
        UArrayMemoryRegion(allocatedArrays, updated)

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> =
        key.ref.mapWithStaticAsSymbolic(
            concreteMapper = { concreteRef -> getAllocatedArray(key.arrayType, key.sort, concreteRef.address).read(key.index) },
            symbolicMapper = { symbolicRef -> getInputArray(key.arrayType, key.sort).read(symbolicRef to key.index) }
        )

    override fun write(
        key: UArrayIndexLValue<ArrayType, Sort, USizeSort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> = foldHeapRefWithStaticAsSymbolic(
        key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val oldRegion = region.getAllocatedArray(key.arrayType, key.sort, concreteRef.address)
            val newRegion = oldRegion.write(key.index, value, innerGuard, ownership)
            region.updateAllocatedArray(concreteRef.address, newRegion, ownership)
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputArray(key.arrayType, key.sort)
            val newRegion = oldRegion.write(symbolicRef to key.index, value, innerGuard, ownership)
            region.updateInput(newRegion)
        }
    )

    override fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: UExpr<USizeSort>,
        fromDstIdx: UExpr<USizeSort>,
        toDstIdx: UExpr<USizeSort>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val srcCollection = region.getAllocatedArray(type, elementSort, srcConcrete.address)
            val dstCollection = region.getAllocatedArray(type, elementSort, dstConcrete.address)
            val adapter = USymbolicArrayAllocatedToAllocatedCopyAdapter(
                fromSrcIdx, fromDstIdx, toDstIdx, USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedArray(dstConcrete.address, newDstCollection, ownership)
        },

        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val srcCollection = region.getAllocatedArray(type, elementSort, srcConcrete.address)
            val dstCollection = region.getInputArray(type, elementSort)
            val adapter = USymbolicArrayAllocatedToInputCopyAdapter(
                fromSrcIdx,
                dstSymbolic to fromDstIdx,
                dstSymbolic to toDstIdx,
                USymbolicArrayIndexKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInput(newDstCollection)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val srcCollection = region.getInputArray(type, elementSort)
            val dstCollection = region.getAllocatedArray(type, elementSort, dstConcrete.address)
            val adapter = USymbolicArrayInputToAllocatedCopyAdapter(
                srcSymbolic to fromSrcIdx,
                fromDstIdx,
                toDstIdx,
                USizeExprKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedArray(dstConcrete.address, newDstCollection, ownership)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val srcCollection = region.getInputArray(type, elementSort)
            val dstCollection = region.getInputArray(type, elementSort)
            val adapter = USymbolicArrayInputToInputCopyAdapter(
                srcSymbolic to fromSrcIdx,
                dstSymbolic to fromDstIdx,
                dstSymbolic to toDstIdx,
                USymbolicArrayIndexKeyInfo()
            )
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInput(newDstCollection)
        },
    )

    override fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<UExpr<USizeSort>, UExpr<Sort>>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UArrayMemoryRegion<ArrayType, Sort, USizeSort> {
        val arrayId = UAllocatedArrayId<_, _, USizeSort>(arrayType, sort, address)
        val newCollection = arrayId.initializedArray(content, operationGuard)
        return UArrayMemoryRegion(allocatedArrays.put(address, newCollection, ownership), inputArray)
    }
}
