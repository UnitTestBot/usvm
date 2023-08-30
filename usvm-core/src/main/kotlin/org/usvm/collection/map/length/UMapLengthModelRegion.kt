package org.usvm.collection.map.length

import io.ksmt.solver.KModel
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UMapLengthModelRegion<MapType>(
    private val regionId: UMapLengthRegionId<MapType>,
) : UReadOnlyMemoryRegion<UMapLengthLValue<MapType>, USizeSort> {
    abstract val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>

    override fun read(key: UMapLengthLValue<MapType>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputMapLength.read(ref)
    }
}

class UMapLengthLazyModelRegion<MapType>(
    regionId: UMapLengthRegionId<MapType>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>
) : UMapLengthModelRegion<MapType>(regionId) {
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort> by lazy {
        inputLengthDecoder.decodeCollection(model, addressesMapping)
    }
}

class UMapLengthEagerModelRegion<MapType>(
    regionId: UMapLengthRegionId<MapType>,
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>
) : UMapLengthModelRegion<MapType>(regionId)
