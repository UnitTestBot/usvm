package org.usvm.collection.array.length

import io.ksmt.solver.KModel
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UArrayLengthModelRegion<ArrayType>(
    private val regionId: UArrayLengthsRegionId<ArrayType>,
) : UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType>, USizeSort> {
    val defaultValue by lazy { regionId.sort.sampleUValue() }

    abstract val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?

    override fun read(key: UArrayLengthLValue<ArrayType>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref) ?: return defaultValue
        return inputArrayLength?.read(ref) ?: defaultValue
    }
}

class UArrayLengthLazyModelRegion<ArrayType>(
    regionId: UArrayLengthsRegionId<ArrayType>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputArrayLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>?
) : UArrayLengthModelRegion<ArrayType>(regionId) {
    override val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>? by lazy {
        inputArrayLengthDecoder?.decodeCollection(model, addressesMapping)
    }
}

class UArrayLengthEagerModelRegion<ArrayType>(
    regionId: UArrayLengthsRegionId<ArrayType>,
    override val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?
) : UArrayLengthModelRegion<ArrayType>(regionId)
