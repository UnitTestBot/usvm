package org.usvm.collection.field

import io.ksmt.solver.KModel
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UFieldsModelRegion<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
) : UFieldsRegion<Field, Sort> {
    abstract val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>?

    override fun read(key: UFieldRef<Field, Sort>): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputFields
            ?.read(ref)
            ?: regionId.sort.sampleUValue()
    }

    override fun write(
        key: UFieldRef<Field, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UFieldRef<Field, Sort>, Sort> {
        error("Illegal operation for a model")
    }
}

class UFieldsLazyModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputFieldsDecoder: UCollectionDecoder<UHeapRef, Sort>?
) : UFieldsModelRegion<Field, Sort>(regionId) {
    override val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>? by lazy {
        inputFieldsDecoder?.decodeCollection(model, addressesMapping)
    }
}

class UFieldsEagerModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    override val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>?
) : UFieldsModelRegion<Field, Sort>(regionId)
