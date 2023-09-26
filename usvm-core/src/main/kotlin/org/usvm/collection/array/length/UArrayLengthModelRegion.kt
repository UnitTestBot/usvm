package org.usvm.collection.array.length

import io.ksmt.solver.KModel
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UArrayLengthModelRegion<ArrayType, USizeSort : USort>(
    private val regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
) : UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> {
    abstract val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>

    override fun read(key: UArrayLengthLValue<ArrayType, USizeSort>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputArrayLength.read(ref)
    }
}

class UArrayLengthLazyModelRegion<ArrayType, USizeSort : USort>(
    regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputArrayLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>,
) : UArrayLengthModelRegion<ArrayType, USizeSort>(regionId) {
    override val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort> by lazy {
        inputArrayLengthDecoder.decodeCollection(model, addressesMapping)
    }
}

class UArrayLengthEagerModelRegion<ArrayType, USizeSort : USort>(
    regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
    override val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>,
) : UArrayLengthModelRegion<ArrayType, USizeSort>(regionId)
