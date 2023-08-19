package org.usvm.model.region

import io.ksmt.solver.KModel
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.collection.region.UArrayLengthRef
import org.usvm.memory.collection.region.UArrayLengthsRegion
import org.usvm.memory.collection.region.UArrayLengthsRegionId
import org.usvm.model.AddressesMapping
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UArrayLengthModelRegion<ArrayType>(
    private val regionId: UArrayLengthsRegionId<ArrayType>,
) : UArrayLengthsRegion<ArrayType> {
    abstract fun getInputArrayLength(): UReadOnlyMemoryRegion<UHeapRef, USizeSort>?

    override fun read(key: UArrayLengthRef<ArrayType>): UExpr<USizeSort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        val ref = key.ref
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS) {
            "Unexpected ref in model: $ref"
        }

        return getInputArrayLength()?.read(ref)
            ?: regionId.sort.sampleUValue()
    }

    override fun write(
        key: UArrayLengthRef<ArrayType>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort> {
        error("Illegal operation for a model")
    }
}

class UArrayLengthLazyModelRegion<ArrayType>(
    regionId: UArrayLengthsRegionId<ArrayType>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputArrayLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>?
) : UArrayLengthModelRegion<ArrayType>(regionId) {
    private var inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>? = null

    override fun getInputArrayLength(): UReadOnlyMemoryRegion<UHeapRef, USizeSort>? {
        if (inputArrayLength == null) {
            inputArrayLength = inputArrayLengthDecoder?.decodeCollection(model, addressesMapping)
        }
        return inputArrayLength
    }
}

class UArrayLengthEagerModelRegion<ArrayType>(
    regionId: UArrayLengthsRegionId<ArrayType>,
    private val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?
) : UArrayLengthModelRegion<ArrayType>(regionId) {
    override fun getInputArrayLength(): UReadOnlyMemoryRegion<UHeapRef, USizeSort>? = inputArrayLength
}
