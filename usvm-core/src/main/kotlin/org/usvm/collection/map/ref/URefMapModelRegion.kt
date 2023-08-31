package org.usvm.collection.map.ref

import io.ksmt.solver.KModel
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class URefMapModelRegion<MapType, ValueSort : USort>(
    private val regionId: URefMapRegionId<MapType, ValueSort>
) : UReadOnlyMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef)
        return inputMap.read(mapRef to key.mapKey)
    }
}

class URefMapLazyModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort>
) : URefMapModelRegion<MapType, ValueSort>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort> by lazy {
        inputMapDecoder.decodeCollection(model, addressesMapping)
    }
}

class URefMapEagerModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>
) : URefMapModelRegion<MapType, ValueSort>(regionId)
