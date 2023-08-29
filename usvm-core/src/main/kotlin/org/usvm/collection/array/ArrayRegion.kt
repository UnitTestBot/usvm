package org.usvm.collection.array

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map

data class UArrayIndexLValue<ArrayType, Sort : USort>(
    override val sort: Sort,
    val ref: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType,
) : ULValue<UArrayIndexLValue<ArrayType, Sort>, Sort> {

    override val memoryRegionId: UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort>, Sort> =
        UArrayRegionId(arrayType, sort)

    override val key: UArrayIndexLValue<ArrayType, Sort>
        get() = this
}

data class UArrayRegionId<ArrayType, Sort : USort>(val arrayType: ArrayType, override val sort: Sort) :
    UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort>, Sort> =
        UArrayMemoryRegion()
}

typealias UAllocatedArray<ArrayType, Sort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>
typealias UInputArray<ArrayType, Sort> = USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>

interface UArrayRegion<ArrayType, Sort : USort> : UMemoryRegion<UArrayIndexLValue<ArrayType, Sort>, Sort> {
    fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ): UArrayRegion<ArrayType, Sort>

    fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<USizeExpr, UExpr<Sort>>,
        guard: UBoolExpr
    ): UArrayRegion<ArrayType, Sort>
}

internal class UArrayMemoryRegion<ArrayType, Sort : USort>(
    private var allocatedArrays: PersistentMap<UConcreteHeapAddress, UAllocatedArray<ArrayType, Sort>> = persistentHashMapOf(),
    private var inputArray: UInputArray<ArrayType, Sort>? = null
) : UArrayRegion<ArrayType, Sort> {

    private fun getAllocatedArray(
        arrayType: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress
    ): UAllocatedArray<ArrayType, Sort> = allocatedArrays[address]
        ?: UAllocatedArrayId(arrayType, sort, address).emptyRegion()

    private fun updateAllocatedArray(ref: UConcreteHeapAddress, updated: UAllocatedArray<ArrayType, Sort>) =
        UArrayMemoryRegion(allocatedArrays.put(ref, updated), inputArray)

    private fun getInputArray(arrayType: ArrayType, sort: Sort): UInputArray<ArrayType, Sort> {
        if (inputArray == null)
            inputArray = UInputArrayId(arrayType, sort).emptyRegion()
        return inputArray!!
    }

    private fun updateInput(updated: UInputArray<ArrayType, Sort>) =
        UArrayMemoryRegion(allocatedArrays, updated)

    override fun read(key: UArrayIndexLValue<ArrayType, Sort>): UExpr<Sort> =
        key.ref.map(
            { concreteRef -> getAllocatedArray(key.arrayType, key.sort, concreteRef.address).read(key.index) },
            { symbolicRef -> getInputArray(key.arrayType, key.sort).read(symbolicRef to key.index) }
        )

    override fun write(
        key: UArrayIndexLValue<ArrayType, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort>, Sort> =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            blockOnConcrete = { region, (concreteRef, innerGuard) ->
                val oldRegion = region.getAllocatedArray(key.arrayType, key.sort, concreteRef.address)
                val newRegion = oldRegion.write(key.index, value, innerGuard)
                region.updateAllocatedArray(concreteRef.address, newRegion)
            },
            blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputArray(key.arrayType, key.sort)
                val newRegion = oldRegion.write(symbolicRef to key.index, value, innerGuard)
                region.updateInput(newRegion)
            }
        )

    override fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ) =
        foldHeapRef(
            srcRef,
            this,
            guard,
            blockOnConcrete = { outerRegion, (srcRef, guard) ->
                foldHeapRef(
                    dstRef,
                    outerRegion,
                    guard,
                    blockOnConcrete = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getAllocatedArray(type, elementSort, srcRef.address)
                        val dstCollection = region.getAllocatedArray(type, elementSort, dstRef.address)
                        val adapter = USymbolicArrayAllocatedToAllocatedCopyAdapter(
                            fromSrcIdx, fromDstIdx, toDstIdx, USizeExprKeyInfo
                        )
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        region.updateAllocatedArray(dstRef.address, newDstCollection)
                    },
                    blockOnSymbolic = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getAllocatedArray(type, elementSort, srcRef.address)
                        val dstCollection = region.getInputArray(type, elementSort)
                        val adapter = USymbolicArrayAllocatedToInputCopyAdapter(
                            fromSrcIdx,
                            dstRef to fromDstIdx,
                            dstRef to toDstIdx,
                            USymbolicArrayIndexKeyInfo
                        )
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        region.updateInput(newDstCollection)
                    },
                )
            },
            blockOnSymbolic = { outerRegion, (srcRef, guard) ->
                foldHeapRef(
                    dstRef,
                    outerRegion,
                    guard,
                    blockOnConcrete = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getInputArray(type, elementSort)
                        val dstCollection = region.getAllocatedArray(type, elementSort, dstRef.address)
                        val adapter = USymbolicArrayInputToAllocatedCopyAdapter(
                            srcRef to fromSrcIdx,
                            fromDstIdx,
                            toDstIdx,
                            USizeExprKeyInfo
                        )
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        region.updateAllocatedArray(dstRef.address, newDstCollection)
                    },
                    blockOnSymbolic = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getInputArray(type, elementSort)
                        val dstCollection = region.getInputArray(type, elementSort)
                        val adapter = USymbolicArrayInputToInputCopyAdapter(
                            srcRef to fromSrcIdx,
                            dstRef to fromDstIdx,
                            dstRef to toDstIdx,
                            USymbolicArrayIndexKeyInfo
                        )
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        region.updateInput(newDstCollection)
                    },
                )
            },
        )

    override fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<USizeExpr, UExpr<Sort>>,
        guard: UBoolExpr
    ): UArrayMemoryRegion<ArrayType, Sort> {
        val arrayId = UAllocatedArrayId(arrayType, sort, address)
        val newCollection = arrayId.initializedArray(content, guard)
        return UArrayMemoryRegion(allocatedArrays.put(address, newCollection), inputArray)
    }
}
