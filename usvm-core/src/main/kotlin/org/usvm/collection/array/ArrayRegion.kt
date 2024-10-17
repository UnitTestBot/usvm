package org.usvm.collection.array

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.foldHeapRef
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

data class UArrayRegionId<ArrayType, Sort : USort, USizeSort : USort>(
    val arrayType: ArrayType,
    override val sort: Sort
) :
    UMemoryRegionId<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> =
        UArrayMemoryRegion()
}

typealias UAllocatedArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>
typealias UInputArray<ArrayType, Sort, USizeSort> = USymbolicCollection<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>

interface UArrayRegion<ArrayType, Sort : USort, USizeSort : USort> :
    UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    fun <SrcKey, SrcSort: USort> memcpy(
        srcCollection: USymbolicCollection<USymbolicCollectionId<SrcKey, SrcSort, *>, SrcKey, SrcSort>,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        operationGuard: UBoolExpr,
        allocatedDstAdapter: (UConcreteHeapRef) -> USymbolicCollectionAdapter<SrcKey, UExpr<USizeSort>, SrcSort, Sort>,
        inputDstAdapter: (UHeapRef) -> USymbolicCollectionAdapter<SrcKey, USymbolicArrayIndex<USizeSort>, SrcSort, Sort>,
    ): UArrayRegion<ArrayType, Sort, USizeSort>

    fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<UExpr<USizeSort>, UExpr<Sort>>,
        operationGuard: UBoolExpr
    ): UArrayRegion<ArrayType, Sort, USizeSort>

    fun getAllocatedArray(
        arrayType: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress
    ): UAllocatedArray<ArrayType, Sort, USizeSort>
}

internal class UArrayMemoryRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private var allocatedArrays: PersistentMap<UConcreteHeapAddress, UAllocatedArray<ArrayType, Sort, USizeSort>> = persistentHashMapOf(),
    private var inputArray: UInputArray<ArrayType, Sort, USizeSort>? = null
) : UArrayRegion<ArrayType, Sort, USizeSort> {

    override fun getAllocatedArray(
        arrayType: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress
    ): UAllocatedArray<ArrayType, Sort, USizeSort> {
        var collection = allocatedArrays[address]
        if (collection == null) {
            collection = UAllocatedArrayId<_, _, USizeSort>(arrayType, sort, address).emptyCollection()
            allocatedArrays = allocatedArrays.put(address, collection)
        }
        return collection
    }

    internal fun updateAllocatedArray(ref: UConcreteHeapAddress, updated: UAllocatedArray<ArrayType, Sort, USizeSort>) =
        UArrayMemoryRegion(allocatedArrays.put(ref, updated), inputArray)

    internal fun getInputArray(arrayType: ArrayType, sort: Sort): UInputArray<ArrayType, Sort, USizeSort> {
        if (inputArray == null)
            inputArray = UInputArrayId<_, _, USizeSort>(arrayType, sort).emptyCollection()
        return inputArray!!
    }

    internal fun updateInput(updated: UInputArray<ArrayType, Sort, USizeSort>) =
        UArrayMemoryRegion(allocatedArrays, updated)

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> =
        key.ref.mapWithStaticAsSymbolic(
            concreteMapper = { concreteRef ->
                getAllocatedArray(
                    key.arrayType,
                    key.sort,
                    concreteRef.address
                ).read(key.index)
            },
            symbolicMapper = { symbolicRef -> getInputArray(key.arrayType, key.sort).read(symbolicRef to key.index) }
        )

    override fun write(
        key: UArrayIndexLValue<ArrayType, Sort, USizeSort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> = foldHeapRefWithStaticAsSymbolic(
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

    override fun <SrcKey, SrcSort: USort> memcpy(
        srcCollection: USymbolicCollection<USymbolicCollectionId<SrcKey, SrcSort, *>, SrcKey, SrcSort>,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        operationGuard: UBoolExpr,
        allocatedDstAdapter: (UConcreteHeapRef) -> USymbolicCollectionAdapter<SrcKey, UExpr<USizeSort>, SrcSort, Sort>,
        inputDstAdapter: (UHeapRef) -> USymbolicCollectionAdapter<SrcKey, USymbolicArrayIndex<USizeSort>, SrcSort, Sort>,
    ): UArrayRegion<ArrayType, Sort, USizeSort> = foldHeapRef(
        ref = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete = { region, guardedDstRef ->
            val dstAddress = guardedDstRef.expr.address
            val dstCollection = region.getAllocatedArray(type, elementSort, dstAddress)
            val adapter = allocatedDstAdapter(guardedDstRef.expr)
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guardedDstRef.guard)
            region.updateAllocatedArray(dstAddress, newDstCollection)
        },
        blockOnSymbolic = { region, guardedDstRef ->
            val dstCollection = region.getInputArray(type, elementSort)
            val adapter = inputDstAdapter(guardedDstRef.expr)
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guardedDstRef.guard)
            region.updateInput(newDstCollection)
        })

    override fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<UExpr<USizeSort>, UExpr<Sort>>,
        operationGuard: UBoolExpr
    ): UArrayMemoryRegion<ArrayType, Sort, USizeSort> {
        val arrayId = UAllocatedArrayId<_, _, USizeSort>(arrayType, sort, address)
        val newCollection = arrayId.initializedArray(content, operationGuard)
        return UArrayMemoryRegion(allocatedArrays.put(address, newCollection), inputArray)
    }
}

fun <Type, SrcSort : USort, DstSort : USort, USizeSort : USort> convertArray(
    srcType: Type,
    dstType: Type,
    srcSort: SrcSort,
    dstSort: DstSort,
    srcReg: UArrayRegion<Type, SrcSort, USizeSort>,
    dstReg: UArrayRegion<Type, DstSort, USizeSort>,
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    fromSrcIdx: UExpr<USizeSort>,
    fromDstIdx: UExpr<USizeSort>,
    toDstIdx: UExpr<USizeSort>,
    operationGuard: UBoolExpr,
    converter: (UExpr<SrcSort>) -> UExpr<DstSort>
): UArrayRegion<Type, DstSort, USizeSort> {
    require(srcReg is UArrayMemoryRegion<Type, SrcSort, USizeSort>) { "Array conversion is unsupported for $srcReg" }
    return foldHeapRef(
        ref = srcRef,
        initial = dstReg,
        initialGuard = operationGuard,
        blockOnConcrete = { region, guardedSrcRef ->
            val srcCollection = srcReg.getAllocatedArray(srcType, srcSort, guardedSrcRef.expr.address)
            region.memcpy(srcCollection, dstRef, dstType, dstSort, operationGuard,
                allocatedDstAdapter = {
                    USymbolicArrayAllocatedToAllocatedCopyAdapter(
                        fromSrcIdx, fromDstIdx, toDstIdx, USizeExprKeyInfo(), converter
                    )
                },
                inputDstAdapter = { dstSymbolic ->
                    USymbolicArrayAllocatedToInputCopyAdapter(
                        fromSrcIdx,
                        dstSymbolic to fromDstIdx,
                        dstSymbolic to toDstIdx,
                        USymbolicArrayIndexKeyInfo(),
                        converter
                    )
                })
        },
        blockOnSymbolic = { region, guardedSrcRef ->
            val srcCollection = srcReg.getInputArray(srcType, srcSort)
            region.memcpy(srcCollection, dstRef, dstType, dstSort, operationGuard,
                allocatedDstAdapter = {
                    USymbolicArrayInputToAllocatedCopyAdapter(
                        guardedSrcRef.expr to fromSrcIdx,
                        fromDstIdx,
                        toDstIdx,
                        USizeExprKeyInfo(),
                        converter
                    )
                },
                inputDstAdapter = { dstSymbolic ->
                    USymbolicArrayInputToInputCopyAdapter(
                        guardedSrcRef.expr to fromSrcIdx,
                        dstSymbolic to fromDstIdx,
                        dstSymbolic to toDstIdx,
                        USymbolicArrayIndexKeyInfo(),
                        converter
                    )
                })
        },
    )
}
