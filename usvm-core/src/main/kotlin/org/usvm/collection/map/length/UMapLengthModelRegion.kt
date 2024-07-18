package org.usvm.collection.map.length

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UMapLengthModelRegion<MapType, USizeSort : USort>(
    private val regionId: UMapLengthRegionId<MapType, USizeSort>,
) : UReadOnlyMemoryRegion<UMapLengthLValue<MapType, USizeSort>, USizeSort> {
    abstract val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>

    override fun read(key: UMapLengthLValue<MapType, USizeSort>, ownership: MutabilityOwnership): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputMapLength.read(ref, ownership)
    }
}

class UMapLengthLazyModelRegion<MapType, USizeSort : USort>(
    regionId: UMapLengthRegionId<MapType, USizeSort>,
    private val model: UModelEvaluator<*>,
    private val inputLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>
) : UMapLengthModelRegion<MapType, USizeSort>(regionId) {
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort> by lazy {
        inputLengthDecoder.decodeCollection(model)
    }
}

class UMapLengthEagerModelRegion<MapType, USizeSort : USort>(
    regionId: UMapLengthRegionId<MapType, USizeSort>,
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>
) : UMapLengthModelRegion<MapType, USizeSort>(regionId)
