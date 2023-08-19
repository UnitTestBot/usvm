package org.usvm.memory.collection.region

import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.adapter.USymbolicMapMergeAdapter
import org.usvm.memory.collection.id.UAllocatedSymbolicMapId
import org.usvm.memory.collection.id.UInputSymbolicMapId
import org.usvm.memory.collection.key.UHeapRefKeyInfo
import org.usvm.memory.collection.key.UHeapRefRegion
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.memory.collection.key.USymbolicMapKey
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.sampleUValue
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

    // Used only for maps with reference type keys
    private var allocatedMapWithAllocatedKeys: PersistentMap<Pair<UConcreteHeapAddress, UConcreteHeapAddress>, UExpr<ValueSort>> = persistentMapOf(),
    private var inputMapWithAllocatedKeys: PersistentMap<UConcreteHeapAddress, UAllocatedSymbolicMap<MapType, UAddressSort, ValueSort, UHeapRefRegion>> = persistentMapOf(),

    // Used for maps with both primitive-type keys and reference type keys
    private var allocatedMapWithInputKeys: PersistentMap<UConcreteHeapAddress, UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg>> = persistentMapOf(),
    private var inputMapWithInputKeys: UInputSymbolicMap<MapType, KeySort, ValueSort, Reg>? = null,
) : USymbolicMapRegion<MapType, KeySort, ValueSort, Reg> {

    private fun updateAllocatedMapWithAllocatedKeys(
        mapRef: UConcreteHeapAddress,
        keyRef: UConcreteHeapAddress,
        guardedValue: UExpr<ValueSort>
    ) = USymbolicMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMapWithAllocatedKeys.put(mapRef to keyRef, guardedValue),
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys,
        inputMapWithInputKeys
    )

    private fun emptyInputMapWithAllocatedKeys(keyAddress: UConcreteHeapAddress) =
        UAllocatedSymbolicMapId(
            valueSort.sampleUValue(),
            keySort.uctx.addressSort,
            valueSort,
            mapType,
            UHeapRefKeyInfo,
            keyAddress,
            null
        ).emptyMap()

    private fun getInputMapWithAllocatedKeys(keyAddress: UConcreteHeapAddress) =
        inputMapWithAllocatedKeys[keyAddress] ?: emptyInputMapWithAllocatedKeys(keyAddress)

    private fun updateInputMapWithAllocatedKeys(
        keyAddress: UConcreteHeapAddress,
        updatedMap: UAllocatedSymbolicMap<MapType, UAddressSort, ValueSort, UHeapRefRegion>
    ) = USymbolicMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys.put(keyAddress, updatedMap),
        allocatedMapWithInputKeys,
        inputMapWithInputKeys
    )

    private fun emptyAllocatedMapWithInputKeys(mapAddress: UConcreteHeapAddress) =
        UAllocatedSymbolicMapId(
            valueSort.sampleUValue(),
            keySort,
            valueSort,
            mapType,
            keyInfo,
            mapAddress,
            null
        ).emptyMap()

    private fun getAllocatedMapWithInputKeys(mapAddress: UConcreteHeapAddress) =
        allocatedMapWithInputKeys[mapAddress] ?: emptyAllocatedMapWithInputKeys(mapAddress)

    private fun updateAllocatedMapWithInputKeys(
        mapAddress: UConcreteHeapAddress,
        updatedMap: UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg>
    ) = USymbolicMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys.put(mapAddress, updatedMap),
        inputMapWithInputKeys
    )

    private fun getInputMapWithInputKeys(): UInputSymbolicMap<MapType, KeySort, ValueSort, Reg> {
        if (inputMapWithInputKeys == null)
            inputMapWithInputKeys = UInputSymbolicMapId(
                keySort,
                valueSort,
                mapType,
                keyInfo,
                null
            ).emptyMap()
        return inputMapWithInputKeys!!
    }

    private fun updateInputMapWithInputKeys(
        updatedMap: UInputSymbolicMap<MapType, KeySort, ValueSort, Reg>
    ) = USymbolicMapMemoryRegion(
        keySort,
        valueSort,
        mapType,
        keyInfo,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys,
        updatedMap
    )

    override fun read(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> =
        if (keySort == keySort.uctx.addressSort) {
            @Suppress("UNCHECKED_CAST")
            readRefKeyMap(key as USymbolicMapEntryRef<MapType, UAddressSort, ValueSort, Reg>)
        } else {
            readNonRefKeyMap(key)
        }

    private fun readNonRefKeyMap(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                getAllocatedMapWithInputKeys(concreteRef.address).read(key.mapKey)
            },
            { symbolicRef -> getInputMapWithInputKeys().read(symbolicRef to key.mapKey) }
        )

    private fun readRefKeyMap(key: USymbolicMapEntryRef<MapType, UAddressSort, ValueSort, Reg>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                key.mapKey.map(
                    { concreteKey ->
                        allocatedMapWithAllocatedKeys[concreteRef.address to concreteKey.address]
                            ?: valueSort.sampleUValue()
                    },
                    { symbolicKey ->
                        getAllocatedMapWithInputKeys(concreteRef.address)
                            .read(symbolicKey.uncheckedCast())
                    }
                )
            },
            { symbolicRef ->
                key.mapKey.map(
                    { concreteKey ->
                        getInputMapWithAllocatedKeys(concreteKey.address).read(symbolicRef)
                    },
                    { symbolicKey -> getInputMapWithInputKeys().read(symbolicRef to symbolicKey.uncheckedCast()) }
                )
            }
        )

    override fun write(
        key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) = if (keySort == keySort.uctx.addressSort) {
        @Suppress("UNCHECKED_CAST")
        writeRefKeyMap(key as USymbolicMapEntryRef<MapType, UAddressSort, ValueSort, Reg>, value, guard)
    } else {
        writeNonRefKeyMap(key, value, guard)
    }

    private fun writeNonRefKeyMap(
        key: USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        initialGuard: UBoolExpr
    ) = foldHeapRef(
        ref = key.mapRef,
        initial = this,
        initialGuard = initialGuard,
        blockOnConcrete = { region, (concreteRef, guard) ->
            val map = region.getAllocatedMapWithInputKeys(concreteRef.address)
            val newMap = map.write(key.mapKey, value, guard)
            region.updateAllocatedMapWithInputKeys(concreteRef.address, newMap)
        },
        blockOnSymbolic = { region, (symbolicRef, guard) ->
            val map = region.getInputMapWithInputKeys()
            val newMap = map.write(symbolicRef to key.mapKey, value, guard)
            region.updateInputMapWithInputKeys(newMap)
        }
    )

    private fun writeRefKeyMap(
        key: USymbolicMapEntryRef<MapType, UAddressSort, ValueSort, Reg>,
        value: UExpr<ValueSort>,
        initialGuard: UBoolExpr
    ) = foldHeapRef(
        ref = key.mapRef,
        initial = this,
        initialGuard = initialGuard,
        blockOnConcrete = { mapRegion, (concreteMapRef, mapGuard) ->
            foldHeapRef(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val guardedValue = guard.uctx.mkIte(
                        guard,
                        { value },
                        {
                            region.allocatedMapWithAllocatedKeys[concreteMapRef.address to concreteKeyRef.address]
                                ?: valueSort.sampleUValue()
                        }
                    )
                    region.updateAllocatedMapWithAllocatedKeys(
                        concreteMapRef.address, concreteKeyRef.address, guardedValue
                    )
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val map = region.getAllocatedMapWithInputKeys(concreteMapRef.address)
                    val newMap = map.write(symbolicKeyRef.uncheckedCast(), value, guard)
                    region.updateAllocatedMapWithInputKeys(concreteMapRef.address, newMap)
                }
            )
        },
        blockOnSymbolic = { mapRegion, (symbolicMapRef, mapGuard) ->
            foldHeapRef(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val map = region.getInputMapWithAllocatedKeys(concreteKeyRef.address)
                    val newMap = map.write(symbolicMapRef, value, guard)
                    region.updateInputMapWithAllocatedKeys(concreteKeyRef.address, newMap)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val map = region.getInputMapWithInputKeys()
                    val newMap = map.write(symbolicMapRef to symbolicKeyRef.uncheckedCast(), value, guard)
                    region.updateInputMapWithInputKeys(newMap)
                }
            )
        }
    )
}
