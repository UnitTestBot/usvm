package org.usvm.collection.array.length

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UArrayLengthModelRegion<ArrayType, USizeSort : USort>(
    private val regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
) : UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> {
    abstract val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>

    override fun read(key: UArrayLengthLValue<ArrayType, USizeSort>, ownership: MutabilityOwnership): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputArrayLength.read(ref, ownership)
    }
}

class UArrayLengthLazyModelRegion<ArrayType, USizeSort : USort>(
    regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val inputArrayLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>,
) : UArrayLengthModelRegion<ArrayType, USizeSort>(regionId) {
    override val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort> by lazy {
        inputArrayLengthDecoder.decodeCollection(model)
    }
}

class UArrayLengthEagerModelRegion<ArrayType, USizeSort : USort>(
    regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
    override val inputArrayLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>,
) : UArrayLengthModelRegion<ArrayType, USizeSort>(regionId)
