package org.usvm.collection.field

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UFieldsModelRegion<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
) : UReadOnlyMemoryRegion<UFieldLValue<Field, Sort>, Sort> {
    abstract val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>

    override fun read(key: UFieldLValue<Field, Sort>, ownership: MutabilityOwnership): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputFields.read(ref, ownership)
    }
}

class UFieldsLazyModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    private val model: UModelEvaluator<*>,
    private val inputFieldsDecoder: UCollectionDecoder<UHeapRef, Sort>
) : UFieldsModelRegion<Field, Sort>(regionId) {
    override val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort> by lazy {
        inputFieldsDecoder.decodeCollection(model)
    }
}

class UFieldsEagerModelRegion<Field, Sort : USort>(
    regionId: UFieldsRegionId<Field, Sort>,
    override val inputFields: UReadOnlyMemoryRegion<UHeapRef, Sort>
) : UFieldsModelRegion<Field, Sort>(regionId)
