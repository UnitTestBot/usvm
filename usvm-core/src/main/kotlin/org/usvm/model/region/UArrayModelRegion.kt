package org.usvm.model.region

import io.ksmt.solver.KModel
import org.usvm.INITIAL_CONCRETE_ADDRESS
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.collection.key.USymbolicArrayIndex
import org.usvm.memory.collection.region.UArrayIndexRef
import org.usvm.memory.collection.region.UArrayRegion
import org.usvm.memory.collection.region.UArrayRegionId
import org.usvm.model.AddressesMapping
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UArrayModelRegion<ArrayType, Sort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort>,
) : UArrayRegion<ArrayType, Sort> {

    abstract fun getAllocatedArray(ref: UConcreteHeapRef): UReadOnlyMemoryRegion<USizeExpr, Sort>?

    abstract fun getInputArray(): UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>?

    override fun read(key: UArrayIndexRef<ArrayType, Sort>): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses
        val ref = key.ref
        require(ref is UConcreteHeapRef) { "Non concrete ref in model: $ref" }

        val value = when {
            ref.address >= INITIAL_CONCRETE_ADDRESS ->
                getAllocatedArray(ref)?.read(key.index)

            ref.address <= INITIAL_INPUT_ADDRESS ->
                getInputArray()?.read(ref to key.index)

            else -> error("Unexpected ref in model: $ref")
        }

        return value ?: regionId.sort.sampleUValue()
    }

    override fun write(
        key: UArrayIndexRef<ArrayType, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> {
        error("Illegal operation for a model")
    }

    override fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr
    ): UArrayRegion<ArrayType, Sort> {
        error("Illegal operation for a model")
    }

    override fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: ArrayType,
        sort: Sort,
        content: Map<USizeExpr, UExpr<Sort>>,
        guard: UBoolExpr
    ): UArrayRegion<ArrayType, Sort> {
        error("Illegal operation for a model")
    }
}

class UArrayLazyModelRegion<ArrayType, Sort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val allocatedArrayDecoder: Map<UConcreteHeapAddress, UCollectionDecoder<USizeExpr, Sort>>,
    private val inputArrayDecoder: UCollectionDecoder<USymbolicArrayIndex, Sort>?
) : UArrayModelRegion<ArrayType, Sort>(regionId) {
    private val decodedAllocatedArrays = mutableMapOf<UConcreteHeapAddress, UReadOnlyMemoryRegion<USizeExpr, Sort>>()
    private var decodedInputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>? = null

    override fun getAllocatedArray(ref: UConcreteHeapRef): UReadOnlyMemoryRegion<USizeExpr, Sort>? =
        decodedAllocatedArrays.getOrPut(ref.address) {
            allocatedArrayDecoder[ref.address]?.decodeCollection(model, addressesMapping) ?: return null
        }

    override fun getInputArray(): UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>? {
        if (decodedInputArray == null) {
            decodedInputArray = inputArrayDecoder?.decodeCollection(model, addressesMapping)
        }
        return decodedInputArray
    }
}

class UArrayEagerModelRegion<ArrayType, Sort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort>,
    private val allocatedArrays: Map<UConcreteHeapAddress, UReadOnlyMemoryRegion<USizeExpr, Sort>>,
    private val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>?
) : UArrayModelRegion<ArrayType, Sort>(regionId) {
    override fun getAllocatedArray(ref: UConcreteHeapRef): UReadOnlyMemoryRegion<USizeExpr, Sort>? =
        allocatedArrays[ref.address]

    override fun getInputArray(): UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>? = inputArray
}
