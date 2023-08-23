package org.usvm.collection.map.ref

import io.ksmt.solver.KModel
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class USymbolicRefMapModelRegion<MapType, ValueSort : USort>(
    private val regionId: USymbolicRefMapRegionId<MapType, ValueSort>
) : USymbolicRefMapRegion<MapType, ValueSort> {
    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>?

    override fun read(key: USymbolicRefMapEntryRef<MapType, ValueSort>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef)
        return inputMap
            ?.read(mapRef to key.mapKey)
            ?: regionId.sort.sampleUValue()
    }

    override fun write(
        key: USymbolicRefMapEntryRef<MapType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): UMemoryRegion<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> {
        error("Illegal operation for a model")
    }
}

class USymbolicRefMapLazyModelRegion<MapType, ValueSort : USort>(
    regionId: USymbolicRefMapRegionId<MapType, ValueSort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort>?
) : USymbolicRefMapModelRegion<MapType, ValueSort>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>? by lazy {
        inputMapDecoder?.decodeCollection(model, addressesMapping)
    }
}

class USymbolicRefMapEagerModelRegion<MapType, ValueSort : USort>(
    regionId: USymbolicRefMapRegionId<MapType, ValueSort>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>?
) : USymbolicRefMapModelRegion<MapType, ValueSort>(regionId)
