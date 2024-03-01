package org.usvm.collection.map.ref

import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
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
    private val model: UModelEvaluator<*>,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort>
) : URefMapModelRegion<MapType, ValueSort>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort> by lazy {
        inputMapDecoder.decodeCollection(model)
    }
}

class URefMapEagerModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>
) : URefMapModelRegion<MapType, ValueSort>(regionId)
