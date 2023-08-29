package org.usvm.collection.array

import io.ksmt.solver.KModel
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UArrayModelRegion<ArrayType, Sort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort>,
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort>, Sort> {
    val defaultValue by lazy { regionId.sort.sampleUValue() }

    abstract val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>?

    override fun read(key: UArrayIndexLValue<ArrayType, Sort>): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref) ?: return defaultValue
        return inputArray?.read(ref to key.index) ?: defaultValue
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
