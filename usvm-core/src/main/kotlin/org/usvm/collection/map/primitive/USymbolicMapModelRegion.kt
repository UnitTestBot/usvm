package org.usvm.collection.map.primitive

import io.ksmt.solver.KModel
import org.usvm.INITIAL_CONCRETE_ADDRESS
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.model.AddressesMapping
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder
import org.usvm.util.Region

abstract class USymbolicMapModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: USymbolicMapRegionId<MapType, KeySort, ValueSort, Reg>
) : USymbolicMapRegion<MapType, KeySort, ValueSort, Reg> {

    abstract fun getAllocatedMap(ref: UConcreteHeapRef): UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>?

    abstract fun getInputMap(): UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>?

    override fun read(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses
        val mapRef = key.mapRef
        require(mapRef is UConcreteHeapRef) { "Non concrete ref in model: $mapRef" }

        val value = when {
            mapRef.address >= INITIAL_CONCRETE_ADDRESS ->
                getAllocatedMap(mapRef)?.read(key.mapKey)

            mapRef.address <= INITIAL_INPUT_ADDRESS ->
                getInputMap()?.read(mapRef to key.mapKey)

            else -> error("Unexpected ref in model: $mapRef")
        }

        return value ?: regionId.sort.sampleUValue()
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
    private val allocatedMapDecoder: Map<UConcreteHeapAddress, UCollectionDecoder<UExpr<KeySort>, ValueSort>>,
    private val inputMapDecoder: UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort>?
) : USymbolicMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    private val decodedAllocatedMap =
        mutableMapOf<UConcreteHeapAddress, UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>>()

    private var decodedInputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>? = null

    override fun getAllocatedMap(ref: UConcreteHeapRef): UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>? =
        decodedAllocatedMap.getOrPut(ref.address) {
            allocatedMapDecoder[ref.address]?.decodeCollection(model, addressesMapping) ?: return null
        }

    override fun getInputMap(): UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>? {
        if (decodedInputMap == null) {
            decodedInputMap = inputMapDecoder?.decodeCollection(model, addressesMapping)
        }
        return decodedInputMap
    }
}

class USymbolicMapEagerModelRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    regionId: USymbolicMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val allocatedMap: Map<UConcreteHeapAddress, UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>>,
    private val inputMap: UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>?
) : USymbolicMapModelRegion<MapType, KeySort, ValueSort, Reg>(regionId) {
    override fun getAllocatedMap(ref: UConcreteHeapRef): UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort>? =
        allocatedMap[ref.address]

    override fun getInputMap(): UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort>? =
        inputMap
}
