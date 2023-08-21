package org.usvm.model.region

import io.ksmt.solver.KModel
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.collection.region.UFieldRef
import org.usvm.memory.collection.region.UFieldsRegion
import org.usvm.memory.collection.region.UFieldsRegionId
import org.usvm.model.AddressesMapping
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UFieldsModelRegion<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
) : UFieldsRegion<Field, Sort> {
    abstract fun getInputFields(): UReadOnlyMemoryRegion<UHeapRef, Sort>?

    override fun read(key: UFieldRef<Field, Sort>): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        val ref = key.ref
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS) {
            "Unexpected ref in model: $ref"
        }

        return getInputFields()?.read(ref)
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
    private var inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>? = null

    override fun getInputFields(): UReadOnlyMemoryRegion<UHeapRef, Sort>? {
        if (inputFields == null) {
            inputFields = inputFieldsDecoder?.decodeCollection(model, addressesMapping)
        }
        return inputFields
    }
}

class UFieldsEagerModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    private val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>?
) : UFieldsModelRegion<Field, Sort>(regionId) {
    override fun getInputFields(): UReadOnlyMemoryRegion<UHeapRef, Sort>? = inputFields
}
