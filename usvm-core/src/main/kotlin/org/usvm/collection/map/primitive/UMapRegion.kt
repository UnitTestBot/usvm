package org.usvm.collection.map.primitive

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.set.primitive.USetRegion
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.foldHeapRef
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.map
import org.usvm.regions.Region
import org.usvm.uctx

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
    : UMemoryRegion<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        srcKeySet: USetRegion<MapType, KeySort, *>,
        initialGuard: UBoolExpr
    ): UMapRegion<MapType, KeySort, ValueSort, Reg>
}

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
    ) = foldHeapRef(
        ref = key.mapRef,
        initial = this,
        initialGuard = guard,
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

    override fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        srcKeySet: USetRegion<MapType, KeySort, *>,
        initialGuard: UBoolExpr
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = initialGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val srcId = UAllocatedMapId(keySort, valueSort, mapType, keyInfo, srcConcrete.address)
            val srcCollection = region.getAllocatedMap(srcId)
            val srcKeys = srcKeySet.allocatedSetElements(srcConcrete.address)

            val dstId = UAllocatedMapId(keySort, valueSort, mapType, keyInfo, dstConcrete.address)
            val dstCollection = region.getAllocatedMap(dstId)

            val adapter = UAllocatedToAllocatedSymbolicMapMergeAdapter(srcKeys)
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedMap(dstId, newDstCollection)
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val srcId = UAllocatedMapId(keySort, valueSort, mapType, keyInfo, srcConcrete.address)
            val srcCollection = region.getAllocatedMap(srcId)
            val srcKeys = srcKeySet.allocatedSetElements(srcConcrete.address)

            val dstCollection = getInputMap()
            val adapter = UAllocatedToInputSymbolicMapMergeAdapter(dstSymbolic, srcKeys)
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputMap(newDstCollection)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val srcCollection = region.getInputMap()
            val srcKeys = srcKeySet.inputSetElements()

            val dstId = UAllocatedMapId(keySort, valueSort, mapType, keyInfo, dstConcrete.address)
            val dstCollection = region.getAllocatedMap(dstId)

            val adapter = UInputToAllocatedSymbolicMapMergeAdapter(srcSymbolic, srcKeys)
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedMap(dstId, newDstCollection)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val srcCollection = region.getInputMap()
            val srcKeys = srcKeySet.inputSetElements()

            val dstCollection = getInputMap()

            val adapter = UInputToInputSymbolicMapMergeAdapter(srcSymbolic, dstSymbolic, srcKeys)
            val newDstCollection = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputMap(newDstCollection)
        },
    )
}
