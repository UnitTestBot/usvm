package org.usvm.collection.map.ref

import io.ksmt.solver.KModel
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.set.USetRegionId
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class URefMapModelRegion<MapType, ValueSort : USort>(
    private val regionId: URefMapRegionId<MapType, ValueSort>
) : URefMapRegion<MapType, ValueSort> {
    val defaultValue by lazy { regionId.sort.sampleUValue() }

    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>?

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef) ?: return defaultValue
        return inputMap?.read(mapRef to key.mapKey) ?: defaultValue
    }

    override fun write(
        key: URefMapEntryLValue<MapType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): UMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
        error("Illegal operation for a model")
    }

    override fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        sort: ValueSort,
        keySet: USetRegionId<MapType, UAddressSort, *>,
        guard: UBoolExpr
    ): URefMapRegion<MapType, ValueSort> {
        error("Illegal operation for a model")
    }
}

class URefMapLazyModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort>?
) : URefMapModelRegion<MapType, ValueSort>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>? by lazy {
        inputMapDecoder?.decodeCollection(model, addressesMapping)
    }
}

class URefMapEagerModelRegion<MapType, ValueSort : USort>(
    regionId: URefMapRegionId<MapType, ValueSort>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort>?
) : URefMapModelRegion<MapType, ValueSort>(regionId)
