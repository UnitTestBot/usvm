package org.usvm.collection.array

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UArrayModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {
    abstract val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort>

    override fun read(key: UArrayIndexLValue<ArrayType, Sort, USizeSort>, ownership: MutabilityOwnership): UExpr<Sort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputArray.read(ref to key.index, ownership)
    }
}

class UArrayLazyModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val inputArrayDecoder: UCollectionDecoder<USymbolicArrayIndex<USizeSort>, Sort>
) : UArrayModelRegion<ArrayType, Sort, USizeSort>(regionId) {
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort> by lazy {
        inputArrayDecoder.decodeCollection(model)
    }
}

class UArrayEagerModelRegion<ArrayType, Sort : USort, USizeSort : USort>(
    regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    override val inputArray: UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort>
) : UArrayModelRegion<ArrayType, Sort, USizeSort>(regionId)
