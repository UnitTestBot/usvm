package org.usvm.collection.map.ref

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.guardedWrite
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map

data class USymbolicRefMapEntryRef<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    val mapRef: UHeapRef,
    val mapKey: UHeapRef,
    val mapType: MapType
) : ULValue<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> {
    override val memoryRegionId: UMemoryRegionId<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> =
        USymbolicRefMapRegionId(sort, mapType)

    override val key: USymbolicRefMapEntryRef<MapType, ValueSort>
        get() = this
}

data class USymbolicRefMapRegionId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    val mapType: MapType,
) : UMemoryRegionId<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> {
    override fun emptyRegion(): UMemoryRegion<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> =
        USymbolicRefMapMemoryRegion(sort, mapType)
}

interface USymbolicRefMapRegion<MapType, ValueSort : USort>
    : UMemoryRegion<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort>

typealias UAllocatedRefMapWithInputKeys<MapType, ValueSort> =
        USymbolicCollection<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UInputRefMapWithAllocatedKeys<MapType, ValueSort> =
        USymbolicCollection<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UInputRefMap<MapType, ValueSort> =
        USymbolicCollection<UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>

internal class USymbolicRefMapMemoryRegion<MapType, ValueSort : USort>(
    private val valueSort: ValueSort,
    private val mapType: MapType,
    private var allocatedMapWithAllocatedKeys: PersistentMap<UAllocatedSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UExpr<ValueSort>> = persistentMapOf(),
    private var inputMapWithAllocatedKeys: PersistentMap<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UInputRefMapWithAllocatedKeys<MapType, ValueSort>> = persistentMapOf(),
    private var allocatedMapWithInputKeys: PersistentMap<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UAllocatedRefMapWithInputKeys<MapType, ValueSort>> = persistentMapOf(),
    private var inputMapWithInputKeys: UInputRefMap<MapType, ValueSort>? = null,
) : USymbolicRefMapRegion<MapType, ValueSort> {

    private fun updateAllocatedMapWithAllocatedKeys(
        updated: PersistentMap<UAllocatedSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UExpr<ValueSort>>
    ) = USymbolicRefMapMemoryRegion(
        valueSort,
        mapType,
        updated,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys,
        inputMapWithInputKeys
    )

    private fun getInputMapWithAllocatedKeys(id: UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>) =
        inputMapWithAllocatedKeys[id] ?: id.emptyRegion()

    private fun updateInputMapWithAllocatedKeys(
        id: UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>,
        updatedMap: UInputRefMapWithAllocatedKeys<MapType, ValueSort>
    ) = USymbolicRefMapMemoryRegion(
        valueSort,
        mapType,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys.put(id, updatedMap),
        allocatedMapWithInputKeys,
        inputMapWithInputKeys
    )

    private fun getAllocatedMapWithInputKeys(id: UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>) =
        allocatedMapWithInputKeys[id] ?: id.emptyRegion()

    private fun updateAllocatedMapWithInputKeys(
        id: UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>,
        updatedMap: UAllocatedRefMapWithInputKeys<MapType, ValueSort>
    ) = USymbolicRefMapMemoryRegion(
        valueSort,
        mapType,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys.put(id, updatedMap),
        inputMapWithInputKeys
    )

    private fun getInputMapWithInputKeys(): UInputRefMap<MapType, ValueSort> {
        if (inputMapWithInputKeys == null)
            inputMapWithInputKeys = UInputSymbolicRefMapWithInputKeysId(
                valueSort, mapType
            ).emptyRegion()
        return inputMapWithInputKeys!!
    }

    private fun updateInputMapWithInputKeys(updatedMap: UInputRefMap<MapType, ValueSort>) =
        USymbolicRefMapMemoryRegion(
            valueSort,
            mapType,
            allocatedMapWithAllocatedKeys,
            inputMapWithAllocatedKeys,
            allocatedMapWithInputKeys,
            updatedMap
        )

    override fun read(key: USymbolicRefMapEntryRef<MapType, ValueSort>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                key.mapKey.map(
                    { concreteKey ->
                        val id = UAllocatedSymbolicRefMapWithAllocatedKeysId(
                            valueSort, mapType, concreteRef.address, concreteKey.address
                        )
                        allocatedMapWithAllocatedKeys[id] ?: id.defaultValue
                    },
                    { symbolicKey ->
                        val id = UAllocatedSymbolicRefMapWithInputKeysId(
                            valueSort, mapType, concreteRef.address
                        )
                        getAllocatedMapWithInputKeys(id).read(symbolicKey)
                    }
                )
            },
            { symbolicRef ->
                key.mapKey.map(
                    { concreteKey ->
                        val id = UInputSymbolicRefMapWithAllocatedKeysId(
                            valueSort, mapType, concreteKey.address
                        )
                        getInputMapWithAllocatedKeys(id).read(symbolicRef)
                    },
                    { symbolicKey ->
                        getInputMapWithInputKeys().read(symbolicRef to symbolicKey)
                    }
                )
            }
        )

    override fun write(
        key: USymbolicRefMapEntryRef<MapType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) = foldHeapRef(
        ref = key.mapRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { mapRegion, (concreteMapRef, mapGuard) ->
            foldHeapRef(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = UAllocatedSymbolicRefMapWithAllocatedKeysId(
                        valueSort, mapType, concreteMapRef.address, concreteKeyRef.address
                    )
                    val newMap = region.allocatedMapWithAllocatedKeys.guardedWrite(id, value, guard) { id.defaultValue }
                    region.updateAllocatedMapWithAllocatedKeys(newMap)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val id = UAllocatedSymbolicRefMapWithInputKeysId(
                        valueSort, mapType, concreteMapRef.address
                    )
                    val map = region.getAllocatedMapWithInputKeys(id)
                    val newMap = map.write(symbolicKeyRef, value, guard)
                    region.updateAllocatedMapWithInputKeys(id, newMap)
                }
            )
        },
        blockOnSymbolic = { mapRegion, (symbolicMapRef, mapGuard) ->
            foldHeapRef(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = UInputSymbolicRefMapWithAllocatedKeysId(valueSort, mapType, concreteKeyRef.address)
                    val map = region.getInputMapWithAllocatedKeys(id)
                    val newMap = map.write(symbolicMapRef, value, guard)
                    region.updateInputMapWithAllocatedKeys(id, newMap)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val map = region.getInputMapWithInputKeys()
                    val newMap = map.write(symbolicMapRef to symbolicKeyRef, value, guard)
                    region.updateInputMapWithInputKeys(newMap)
                }
            )
        }
    )
}
