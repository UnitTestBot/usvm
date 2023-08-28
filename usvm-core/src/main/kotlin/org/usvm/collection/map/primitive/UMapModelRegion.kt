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

abstract class UMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>
) : UMapRegion<MapType, KeySort, ValueSort, Reg> {
    val defaultValue by lazy { regionId.sort.sampleUValue() }

    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>?

    override fun read(key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef) ?: return defaultValue
        return inputMap?.read(mapRef to key.mapKey) ?: defaultValue
    }

    override fun write(
        key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ): UMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {
        error("Illegal operation for a model")
    }
}

class UMapLazyModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort>?
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>? by lazy {
        inputMapDecoder?.decodeCollection(model, addressesMapping)
    }
}

class UMapEagerModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>?
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId)
