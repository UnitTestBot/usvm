package org.usvm.collection.map.primitive

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.USymbolicCollection
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.uctx
import org.usvm.util.Region

data class USymbolicMapEntryRef<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    override val sort: ValueSort,
    val mapRef: UHeapRef,
    val mapKey: UExpr<KeySort>,
    val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : ULValue<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> {

    override val memoryRegionId: UMemoryRegionId<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> =
        USymbolicMapRegionId(keySort, sort, mapType, keyInfo)

    override val key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg> = this
}

data class USymbolicMapRegionId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    val keySort: KeySort,
    override val sort: ValueSort,
    val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
) : UMemoryRegionId<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    override fun emptyRegion(): UMemoryRegion<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> =
        USymbolicMapMemoryRegion(keySort, sort, mapType, keyInfo)
}

typealias UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg> =
        USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>

typealias UInputSymbolicMap<MapType, KeySort, ValueSort, Reg> =
        USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>

interface USymbolicMapRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>
    : UMemoryRegion<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort>

internal class USymbolicMapMemoryRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val keySort: KeySort,
    private val valueSort: ValueSort,
    private val mapType: MapType,
    private val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    private var allocatedMaps: PersistentMap<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg>> = persistentMapOf(),
    private var inputMap: UInputSymbolicMap<MapType, KeySort, ValueSort, Reg>? = null,
) : USymbolicMapRegion<MapType, KeySort, ValueSort, Reg> {
    init {
        check(keySort != keySort.uctx.addressSort) {
            "Ref map must be used to handle maps with ref keys"
        }
    }

    private fun getAllocatedMap(id: UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>) =
        allocatedMaps[id] ?: id.emptyRegion()

    private fun updateAllocatedMap(
        id: UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>,
        updatedMap: UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg>
    ) = USymbolicMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMaps.put(id, updatedMap),
        inputMap
    )

    private fun getInputMap(): UInputSymbolicMap<MapType, KeySort, ValueSort, Reg> {
        if (inputMap == null)
            inputMap = UInputSymbolicMapId(keySort, valueSort, mapType, keyInfo).emptyRegion()
        return inputMap!!
    }

    private fun updateInputMap(
        updatedMap: UInputSymbolicMap<MapType, KeySort, ValueSort, Reg>
    ) = USymbolicMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMaps,
        updatedMap
    )

    override fun read(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                val id = UAllocatedSymbolicMapId(keySort, valueSort, mapType, keyInfo, concreteRef.address)
                getAllocatedMap(id).read(key.mapKey)
            },
            { symbolicRef ->
                getInputMap().read(symbolicRef to key.mapKey)
            }
        )

    override fun write(
        key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) = writeNonRefKeyMap(key, value, guard)

    private fun writeNonRefKeyMap(
        key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        initialGuard: UBoolExpr
    ) = foldHeapRef(
        ref = key.mapRef,
        initial = this,
        initialGuard = initialGuard,
        blockOnConcrete = { region, (concreteRef, guard) ->
            val id = UAllocatedSymbolicMapId(keySort, valueSort, mapType, keyInfo, concreteRef.address)
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
