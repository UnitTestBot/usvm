package org.usvm.collection.map.primitive

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder
import org.usvm.regions.Region

abstract class UMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>
) : UReadOnlyMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    abstract val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>

    override fun read(
        key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>,
        ownership: MutabilityOwnership,
    ): UExpr<ValueSort> {
        val mapRef = modelEnsureConcreteInputRef(key.mapRef)
        return inputMap.read(mapRef to key.mapKey, ownership)
    }
}

class UMapLazyModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val model: UModelEvaluator<*>,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort>
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort> by lazy {
        inputMapDecoder.decodeCollection(model)
    }
}

class UMapEagerModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    override val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>
) : UMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId)
