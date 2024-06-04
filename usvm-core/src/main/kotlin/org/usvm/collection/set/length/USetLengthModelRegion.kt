package org.usvm.collection.set.length

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class USetLengthModelRegion<SetType, USizeSort : USort>(
    private val regionId: USetLengthRegionId<SetType, USizeSort>,
) : UReadOnlyMemoryRegion<USetLengthLValue<SetType, USizeSort>, USizeSort> {
    abstract val inputSetLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>

    override fun read(key: USetLengthLValue<SetType, USizeSort>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputSetLength.read(ref)
    }
}

class USetLengthLazyModelRegion<SetType, USizeSort : USort>(
    regionId: USetLengthRegionId<SetType, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val inputLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>
) : USetLengthModelRegion<SetType, USizeSort>(regionId) {
    override val inputSetLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort> by lazy {
        inputLengthDecoder.decodeCollection(model)
    }
}

class USetLengthEagerModelRegion<SetType, USizeSort : USort>(
    regionId: USetLengthRegionId<SetType, USizeSort>,
    override val inputSetLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>
) : USetLengthModelRegion<SetType, USizeSort>(regionId)
