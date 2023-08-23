package org.usvm.collection.array

import io.ksmt.solver.KModel
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UArrayModelRegion<ArrayType, Sort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort>,
) : UArrayRegion<ArrayType, Sort> {

    abstract val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>?

    override fun read(key: UArrayIndexRef<ArrayType, Sort>): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputArray
            ?.read(ref to key.index)
            ?: regionId.sort.sampleUValue()
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
    private val inputArrayDecoder: UCollectionDecoder<USymbolicArrayIndex, Sort>?
) : UArrayModelRegion<ArrayType, Sort>(regionId) {
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>? by lazy {
        inputArrayDecoder?.decodeCollection(model, addressesMapping)
    }
}

class UArrayEagerModelRegion<ArrayType, Sort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort>,
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>?
) : UArrayModelRegion<ArrayType, Sort>(regionId)
