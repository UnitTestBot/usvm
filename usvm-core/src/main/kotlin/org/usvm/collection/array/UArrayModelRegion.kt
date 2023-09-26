package org.usvm.collection.array

import io.ksmt.solver.KModel
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UArrayModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    abstract val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort>

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputArray.read(ref to key.index)
    }
}

class UArrayLazyModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputArrayDecoder: UCollectionDecoder<USymbolicArrayIndex<USizeSort>, Sort>
) : UArrayModelRegion<ArrayType, Sort, USizeSort>(regionId) {
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort> by lazy {
        inputArrayDecoder.decodeCollection(model, addressesMapping)
    }
}

class UArrayEagerModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort>
) : UArrayModelRegion<ArrayType, Sort, USizeSort>(regionId)
