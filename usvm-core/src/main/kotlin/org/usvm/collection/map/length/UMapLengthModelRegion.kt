package org.usvm.collection.map.length

import io.ksmt.solver.KModel
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class UMapLengthModelRegion<MapType, USizeSort : USort>(
    private val regionId: UMapLengthRegionId<MapType, USizeSort>,
) : UReadOnlyMemoryRegion<UMapLengthLValue<MapType, USizeSort>, USizeSort> {
    abstract val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>

    override fun read(key: UMapLengthLValue<MapType, USizeSort>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputMapLength.read(ref)
    }
}

class UMapLengthLazyModelRegion<MapType, USizeSort : USort>(
    regionId: UMapLengthRegionId<MapType, USizeSort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>
) : UMapLengthModelRegion<MapType, USizeSort>(regionId) {
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort> by lazy {
        inputLengthDecoder.decodeCollection(model, addressesMapping)
    }
}

class UMapLengthEagerModelRegion<MapType, USizeSort : USort>(
    regionId: UMapLengthRegionId<MapType, USizeSort>,
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>
) : UMapLengthModelRegion<MapType, USizeSort>(regionId)
