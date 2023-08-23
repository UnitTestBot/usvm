package org.usvm.collection.map.primitive

import io.ksmt.solver.KModel
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
import org.usvm.util.Region

abstract class USymbolicMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: USymbolicMapRegionId<MapType, KeySort, ValueSort, Reg>
) : USymbolicMapRegion<MapType, KeySort, ValueSort, Reg> {
    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>?

    override fun read(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef)
        return inputMap
            ?.read(mapRef to key.mapKey)
            ?: regionId.sort.sampleUValue()
    }

    override fun write(
        key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): UMemoryRegion<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> {
        error("Illegal operation for a model")
    }
}

class USymbolicMapLazyModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: USymbolicMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort>?
) : USymbolicMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>? by lazy {
        inputMapDecoder?.decodeCollection(model, addressesMapping)
    }
}

class USymbolicMapEagerModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: USymbolicMapRegionId<MapType, KeySort, ValueSort, Reg>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>?
) : USymbolicMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId)
