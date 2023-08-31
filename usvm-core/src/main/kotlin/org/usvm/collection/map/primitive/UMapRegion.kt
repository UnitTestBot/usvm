package org.usvm.collection.map.primitive

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.uctx
import org.usvm.util.Region

data class UMapEntryLValue<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    override val sort: ValueSort,
    val mapRef: UHeapRef,
    val mapKey: UExpr<KeySort>,
    val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : ULValue<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {

    override val memoryRegionId: UMemoryRegionId<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> =
        UMapRegionId(keySort, sort, mapType, keyInfo)

    override val key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>
        get() = this
}

data class UMapRegionId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    override val sort: ValueSort,
    val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : UMemoryRegionId<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    override fun emptyRegion(): UMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> =
        UMapMemoryRegion(keySort, sort, mapType, keyInfo)
}

typealias UAllocatedMap<MapType, KeySort, ValueSort, Reg> =
        USymbolicCollection<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>

typealias UInputMap<MapType, KeySort, ValueSort, Reg> =
        USymbolicCollection<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>

interface UMapRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>
    : UMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort>

internal class UMapMemoryRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val keySort: KeySort,
    private val valueSort: ValueSort,
    private val mapType: MapType,
    private val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    private var allocatedMaps: PersistentMap<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UAllocatedMap<MapType, KeySort, ValueSort, Reg>> = persistentMapOf(),
    private var inputMap: UInputMap<MapType, KeySort, ValueSort, Reg>? = null,
) : UMapRegion<MapType, KeySort, ValueSort, Reg> {
    init {
        check(keySort != keySort.uctx.addressSort) {
            "Ref map must be used to handle maps with ref keys"
        }
    }

    private fun getAllocatedMap(
        id: UAllocatedMapId<MapType, KeySort, ValueSort, Reg>
    ): UAllocatedMap<MapType, KeySort, ValueSort, Reg> {
        var collection = allocatedMaps[id]
        if (collection == null) {
            collection = id.emptyRegion()
            allocatedMaps = allocatedMaps.put(id, collection)
        }
        return collection
    }

    private fun updateAllocatedMap(
        id: UAllocatedMapId<MapType, KeySort, ValueSort, Reg>,
        updatedMap: UAllocatedMap<MapType, KeySort, ValueSort, Reg>
    ) = UMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMaps.put(id, updatedMap),
        inputMap
    )

    private fun getInputMap(): UInputMap<MapType, KeySort, ValueSort, Reg> {
        if (inputMap == null)
            inputMap = UInputMapId(keySort, valueSort, mapType, keyInfo).emptyRegion()
        return inputMap!!
    }

    private fun updateInputMap(
        updatedMap: UInputMap<MapType, KeySort, ValueSort, Reg>
    ) = UMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMaps,
        updatedMap
    )

    override fun read(key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                val id = UAllocatedMapId(keySort, valueSort, mapType, keyInfo, concreteRef.address)
                getAllocatedMap(id).read(key.mapKey)
            },
            { symbolicRef ->
                getInputMap().read(symbolicRef to key.mapKey)
            }
        )

    override fun write(
        key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) = writeNonRefKeyMap(key, value, guard)

    private fun writeNonRefKeyMap(
        key: UMapEntryLValue<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        initialGuard: UBoolExpr
    ) = foldHeapRef(
        ref = key.mapRef,
        initial = this,
        initialGuard = initialGuard,
        blockOnConcrete = { region, (concreteRef, guard) ->
            val id = UAllocatedMapId(keySort, valueSort, mapType, keyInfo, concreteRef.address)
            val map = region.getAllocatedMap(id)
            val newMap = map.write(key.mapKey, value, guard)
            region.updateAllocatedMap(id, newMap)
        },
        blockOnSymbolic = { region, (symbolicRef, guard) ->
            val map = region.getInputMap()
            val newMap = map.write(symbolicRef to key.mapKey, value, guard)
            region.updateInputMap(newMap)
        }
    )
}
