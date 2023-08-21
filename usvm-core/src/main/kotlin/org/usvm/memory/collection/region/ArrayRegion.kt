package org.usvm.memory.collection.region

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.adapter.USymbolicArrayCopyAdapter
import org.usvm.memory.collection.id.UAllocatedArrayId
import org.usvm.memory.collection.id.UInputArrayId
import org.usvm.memory.collection.key.USizeExprKeyInfo
import org.usvm.memory.collection.key.USymbolicArrayIndex
import org.usvm.memory.collection.key.USymbolicArrayIndexKeyInfo
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.sampleUValue
import org.usvm.uctx

data class UArrayIndexRef<ArrayType, Sort : USort>(
    override val sort: Sort,
    val ref: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType,
) : ULValue<UArrayIndexRef<ArrayType, Sort>, Sort> {

    override val memoryRegionId: UMemoryRegionId<UArrayIndexRef<ArrayType, Sort>, Sort> =
        UArrayRegionId(arrayType, sort)

    override val key: UArrayIndexRef<ArrayType, Sort> = this
}

data class UArrayRegionId<ArrayType, Sort : USort>(val arrayType: ArrayType, override val sort: Sort) :
    UMemoryRegionId<UArrayIndexRef<ArrayType, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> =
        UArrayMemoryRegion()
}

typealias UAllocatedArray<ArrayType, Sort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>
typealias UInputArray<ArrayType, Sort> = USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>

interface UArrayRegion<ArrayType, Sort : USort> : UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> {
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
        ?: UAllocatedArrayId(arrayType, sort, sort.sampleUValue(), address).emptyRegion()

    private fun updateAllocatedArray(ref: UConcreteHeapAddress, updated: UAllocatedArray<ArrayType, Sort>) =
        UArrayMemoryRegion(allocatedArrays.put(ref, updated), inputArray)

    private fun getInputArray(arrayType: ArrayType, sort: Sort): UInputArray<ArrayType, Sort> {
        if (inputArray == null)
            inputArray = UInputArrayId(arrayType, sort).emptyRegion()
        return inputArray!!
    }

    private fun updateInput(updated: UInputArray<ArrayType, Sort>) =
        UArrayMemoryRegion(allocatedArrays, updated)

    override fun read(key: UArrayIndexRef<ArrayType, Sort>): UExpr<Sort> =
        key.ref.map(
            { concreteRef -> getAllocatedArray(key.arrayType, key.sort, concreteRef.address).read(key.index) },
            { symbolicRef -> getInputArray(key.arrayType, key.sort).read(symbolicRef to key.index) }
        )

    override fun write(
        key: UArrayIndexRef<ArrayType, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> =
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
                        val adapter = USymbolicArrayCopyAdapter(fromSrcIdx, fromDstIdx, toDstIdx, USizeExprKeyInfo)
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        region.updateAllocatedArray(dstRef.address, newDstCollection)
                    },
                    blockOnSymbolic = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getAllocatedArray(type, elementSort, srcRef.address)
                        val dstCollection = region.getInputArray(type, elementSort)
                        val adapter = USymbolicArrayCopyAdapter(
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
                        val adapter = USymbolicArrayCopyAdapter(
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
                        val adapter = USymbolicArrayCopyAdapter(
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
        val arrayId = UAllocatedArrayId(arrayType, sort, sort.sampleUValue(), address)
        val newCollection = arrayId.initializedArray(content, guard)
        return UArrayMemoryRegion(allocatedArrays.put(address, newCollection), inputArray)
    }
}

internal fun <ArrayType, Sort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrcIdx: USizeExpr,
    fromDstIdx: USizeExpr,
    toDstIdx: USizeExpr,
    guard: UBoolExpr,
) {
    val regionId = UArrayRegionId(type, elementSort)
    val region = getRegion(regionId) as UArrayRegion<ArrayType, Sort>
    val newRegion = region.memcpy(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard)
    setRegion(regionId, newRegion)
}

internal fun <ArrayType, Sort : USort> UWritableMemory<ArrayType>.allocateArrayInitialized(
    type: ArrayType,
    elementSort: Sort,
    contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = with(elementSort.uctx) {
    val arrayValues = hashMapOf<USizeExpr, UExpr<Sort>>()
    contents.forEachIndexed { idx, value -> arrayValues[mkSizeExpr(idx)] = value }

    val arrayLength = mkSizeExpr(arrayValues.size)
    val address = allocateArray(type, arrayLength)

    val regionId = UArrayRegionId(type, elementSort)
    val region = getRegion(regionId) as UArrayRegion<ArrayType, Sort>

    val newRegion = region.initializeAllocatedArray(address.address, type, elementSort, arrayValues, guard = trueExpr)

    setRegion(regionId, newRegion)

    return address
}

internal fun <ArrayType> UWritableMemory<ArrayType>.allocateArray(
    type: ArrayType,
    length: USizeExpr
): UConcreteHeapRef {
    val address = alloc(type)

    val lengthRegionRef = UArrayLengthRef(length.sort, address, type)
    write(lengthRegionRef, length, guard = length.uctx.trueExpr)

    return address
}

internal fun <ArrayType, Sort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    contents: Sequence<UExpr<Sort>>,
) = with(sort.uctx) {
    val tmpArrayRef = allocateArrayInitialized(type, sort, contents)
    val contentLength = read(UArrayLengthRef(sizeSort, tmpArrayRef, type))

    memcpy(
        srcRef = tmpArrayRef,
        dstRef = ref,
        type = type,
        elementSort = sort,
        fromSrcIdx = mkSizeExpr(0),
        fromDstIdx = mkSizeExpr(0),
        toDstIdx = contentLength,
        guard = trueExpr
    )

    write(UArrayLengthRef(sizeSort, ref, type), contentLength, guard = trueExpr)
}
