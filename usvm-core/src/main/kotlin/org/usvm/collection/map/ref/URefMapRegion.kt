package org.usvm.collection.map.ref

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegion
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.guardedWrite
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.sampleUValue
import org.usvm.uctx

data class URefMapEntryLValue<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    val mapRef: UHeapRef,
    val mapKey: UHeapRef,
    val mapType: MapType
) : ULValue<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    override val memoryRegionId: UMemoryRegionId<URefMapEntryLValue<MapType, ValueSort>, ValueSort> =
        URefMapRegionId(sort, mapType)

    override val key: URefMapEntryLValue<MapType, ValueSort>
        get() = this
}

data class URefMapRegionId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    val mapType: MapType,
) : UMemoryRegionId<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    override fun emptyRegion(): UMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> =
        URefMapMemoryRegion(sort, mapType)
}

interface URefMapRegion<MapType, ValueSort : USort>
    : UMemoryRegion<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        sort: ValueSort,
        keySet: URefSetRegion<MapType>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): URefMapRegion<MapType, ValueSort>
}

typealias UAllocatedRefMapWithInputKeys<MapType, ValueSort> =
        USymbolicCollection<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UInputRefMapWithAllocatedKeys<MapType, ValueSort> =
        USymbolicCollection<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>

typealias UInputRefMap<MapType, ValueSort> =
        USymbolicCollection<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>

internal data class UAllocatedRefMapWithAllocatedKeysId(
    val mapAddress: UConcreteHeapAddress,
    val keyAddress: UConcreteHeapAddress
)

internal class URefMapMemoryRegion<MapType, ValueSort : USort>(
    private val valueSort: ValueSort,
    private val mapType: MapType,
    private var allocatedMapWithAllocatedKeys: UPersistentHashMap<UAllocatedRefMapWithAllocatedKeysId, UExpr<ValueSort>> = persistentHashMapOf(),
    private var inputMapWithAllocatedKeys: UPersistentHashMap<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UInputRefMapWithAllocatedKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var allocatedMapWithInputKeys: UPersistentHashMap<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UAllocatedRefMapWithInputKeys<MapType, ValueSort>> = persistentHashMapOf(),
    private var inputMapWithInputKeys: UInputRefMap<MapType, ValueSort>? = null,
) : URefMapRegion<MapType, ValueSort> {

    private val defaultOwnership = valueSort.uctx.defaultOwnership

    private fun updateAllocatedMapWithAllocatedKeys(
        updated: UPersistentHashMap<UAllocatedRefMapWithAllocatedKeysId, UExpr<ValueSort>>
    ) = URefMapMemoryRegion(
        valueSort,
        mapType,
        updated,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys,
        inputMapWithInputKeys
    )

    private fun inputMapWithAllocatedKeyId(keyAddress: UConcreteHeapAddress) =
        UInputRefMapWithAllocatedKeysId(valueSort, mapType, keyAddress)

    private fun getInputMapWithAllocatedKeys(
        id: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>
    ): UInputRefMapWithAllocatedKeys<MapType, ValueSort> {
        val (updatedMap, collection) = inputMapWithAllocatedKeys.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        inputMapWithAllocatedKeys = updatedMap
        return collection
    }

    private fun updateInputMapWithAllocatedKeys(
        id: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>,
        updatedMap: UInputRefMapWithAllocatedKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = URefMapMemoryRegion(
        valueSort,
        mapType,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys.put(id, updatedMap, ownership),
        allocatedMapWithInputKeys,
        inputMapWithInputKeys
    )

    private fun allocatedMapWithInputKeyId(mapAddress: UConcreteHeapAddress) =
        UAllocatedRefMapWithInputKeysId(valueSort, mapType, mapAddress)

    private fun getAllocatedMapWithInputKeys(
        id: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>
    ): UAllocatedRefMapWithInputKeys<MapType, ValueSort> {
        val (updatedMap, collection) = allocatedMapWithInputKeys.getOrPut(id, defaultOwnership) { id.emptyRegion() }
        allocatedMapWithInputKeys = updatedMap
        return collection
    }

    private fun updateAllocatedMapWithInputKeys(
        id: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>,
        updatedMap: UAllocatedRefMapWithInputKeys<MapType, ValueSort>,
        ownership: MutabilityOwnership,
    ) = URefMapMemoryRegion(
        valueSort,
        mapType,
        allocatedMapWithAllocatedKeys,
        inputMapWithAllocatedKeys,
        allocatedMapWithInputKeys.put(id, updatedMap, ownership),
        inputMapWithInputKeys
    )

    private fun getInputMapWithInputKeys(): UInputRefMap<MapType, ValueSort> {
        if (inputMapWithInputKeys == null)
            inputMapWithInputKeys = UInputRefMapWithInputKeysId(
                valueSort, mapType
            ).emptyRegion()
        return inputMapWithInputKeys!!
    }

    private fun updateInputMapWithInputKeys(updatedMap: UInputRefMap<MapType, ValueSort>) =
        URefMapMemoryRegion(
            valueSort,
            mapType,
            allocatedMapWithAllocatedKeys,
            inputMapWithAllocatedKeys,
            allocatedMapWithInputKeys,
            updatedMap
        )

    override fun read(key: URefMapEntryLValue<MapType, ValueSort>): UExpr<ValueSort> =
        key.mapRef.mapWithStaticAsSymbolic(
            concreteMapper = { concreteRef ->
                key.mapKey.mapWithStaticAsSymbolic(
                    concreteMapper = { concreteKey ->
                        val id = UAllocatedRefMapWithAllocatedKeysId(concreteRef.address, concreteKey.address)
                        allocatedMapWithAllocatedKeys[id] ?: valueSort.sampleUValue()
                    },
                    symbolicMapper = { symbolicKey ->
                        val id = allocatedMapWithInputKeyId(concreteRef.address)
                        getAllocatedMapWithInputKeys(id).read(symbolicKey)
                    }
                )
            },
            symbolicMapper = { symbolicRef ->
                key.mapKey.mapWithStaticAsSymbolic(
                    concreteMapper = { concreteKey ->
                        val id = inputMapWithAllocatedKeyId(concreteKey.address)
                        getInputMapWithAllocatedKeys(id).read(symbolicRef)
                    },
                    symbolicMapper = { symbolicKey ->
                        getInputMapWithInputKeys().read(symbolicRef to symbolicKey)
                    }
                )
            }
        )

    override fun write(
        key: URefMapEntryLValue<MapType, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRefWithStaticAsSymbolic(
        ref = key.mapRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { mapRegion, (concreteMapRef, mapGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = UAllocatedRefMapWithAllocatedKeysId(concreteMapRef.address, concreteKeyRef.address)
                    val newMap = region.allocatedMapWithAllocatedKeys.guardedWrite(id, value, guard, ownership) {
                        valueSort.sampleUValue()
                    }
                    region.updateAllocatedMapWithAllocatedKeys(newMap)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val id = allocatedMapWithInputKeyId(concreteMapRef.address)
                    val newMap = region.getAllocatedMapWithInputKeys(id)
                        .write(symbolicKeyRef, value, guard, ownership)
                    region.updateAllocatedMapWithInputKeys(id, newMap, ownership)
                }
            )
        },
        blockOnSymbolic = { mapRegion, (symbolicMapRef, mapGuard) ->
            foldHeapRefWithStaticAsSymbolic(
                ref = key.mapKey,
                initial = mapRegion,
                initialGuard = mapGuard,
                blockOnConcrete = { region, (concreteKeyRef, guard) ->
                    val id = inputMapWithAllocatedKeyId(concreteKeyRef.address)
                    val newMap = region.getInputMapWithAllocatedKeys(id)
                        .write(symbolicMapRef, value, guard, ownership)
                    region.updateInputMapWithAllocatedKeys(id, newMap, ownership)
                },
                blockOnSymbolic = { region, (symbolicKeyRef, guard) ->
                    val newMap = region.getInputMapWithInputKeys()
                        .write(symbolicMapRef to symbolicKeyRef, value, guard, ownership)
                    region.updateInputMapWithInputKeys(newMap)
                }
            )
        }
    )

    /**
     * Merge maps with ref keys.
     *
     * Note 1: there are no concrete keys in input maps.
     * Therefore, we can enumerate all possible concrete keys.
     *
     * Note 2: concrete keys can't intersect with symbolic ones.
     *
     * Merge:
     * 1. Merge src symbolic keys into dst symbolic keys using `merge update node`.
     * 2. Merge src concrete keys into dst concrete keys.
     *  2.1 enumerate all concrete keys using map writes.
     *  2.2 write keys into dst with `map.write` operation.
     * */
    override fun merge(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        mapType: MapType,
        sort: ValueSort,
        keySet: URefSetRegion<MapType>,
        operationGuard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val initialAllocatedMapState = region.allocatedMapWithAllocatedKeys
            val updatedAllocatedMap = region.mergeAllocatedMapAllocatedKeys(
                initial = initialAllocatedMapState,
                srcMapRef = srcConcrete,
                guard = guard,
                keySet = keySet,
                read = { initialAllocatedMapState[it] ?: valueSort.sampleUValue() },
                mkDstKeyId = { UAllocatedRefMapWithAllocatedKeysId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { valueSort.sampleUValue() }
                }
            )
            val updatedRegion = region.updateAllocatedMapWithAllocatedKeys(updatedAllocatedMap)

            val srcKeys = keySet.allocatedSetWithInputElements(srcConcrete.address)
            val srcInputKeysId = updatedRegion.allocatedMapWithInputKeyId(srcConcrete.address)
            val srcInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(srcInputKeysId)

            val dstInputKeysId = updatedRegion.allocatedMapWithInputKeyId(dstConcrete.address)
            val dstInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(dstInputKeysId)

            val adapter = UAllocatedToAllocatedSymbolicRefMapMergeAdapter(srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateAllocatedMapWithInputKeys(dstInputKeysId, updatedDstCollection, ownership)
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val initialAllocatedMapState = region.allocatedMapWithAllocatedKeys
            val updatedRegion = region.mergeAllocatedMapAllocatedKeys(
                initial = region, srcMapRef = srcConcrete, guard = guard, keySet = keySet,
                read = { initialAllocatedMapState[it] ?: valueSort.sampleUValue() },
                mkDstKeyId = { inputMapWithAllocatedKeyId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputMapWithAllocatedKeys(dstKeyId)
                        .write(dstSymbolic, value, g, ownership)
                    result.updateInputMapWithAllocatedKeys(dstKeyId, newMap, ownership)
                }
            )

            val srcKeys = keySet.allocatedSetWithInputElements(srcConcrete.address)
            val srcInputKeysId = updatedRegion.allocatedMapWithInputKeyId(srcConcrete.address)
            val srcInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(srcInputKeysId)

            val dstInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val adapter = UAllocatedToInputSymbolicRefMapMergeAdapter(dstSymbolic, srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateInputMapWithInputKeys(updatedDstCollection)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val updatedAllocatedMap = region.mergeInputMapAllocatedKeys(
                initial = region.allocatedMapWithAllocatedKeys,
                srcMapRef = srcSymbolic, guard = guard, keySet = keySet,
                read = { region.getInputMapWithAllocatedKeys(it).read(srcSymbolic) },
                mkDstKeyId = { UAllocatedRefMapWithAllocatedKeysId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g, ownership) { sort.sampleUValue() }
                }
            )
            val updatedRegion = region.updateAllocatedMapWithAllocatedKeys(updatedAllocatedMap)

            val srcKeys = keySet.inputSetWithInputElements()
            val srcInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val dstInputKeysId = updatedRegion.allocatedMapWithInputKeyId(dstConcrete.address)
            val dstInputKeysCollection = updatedRegion.getAllocatedMapWithInputKeys(dstInputKeysId)

            val adapter = UInputToAllocatedSymbolicRefMapMergeAdapter(srcSymbolic, srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateAllocatedMapWithInputKeys(dstInputKeysId, updatedDstCollection, ownership)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val updatedRegion = region.mergeInputMapAllocatedKeys(
                initial = region, srcMapRef = srcSymbolic, guard = guard, keySet = keySet,
                read = { region.getInputMapWithAllocatedKeys(it).read(srcSymbolic) },
                mkDstKeyId = { inputMapWithAllocatedKeyId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputMapWithAllocatedKeys(dstKeyId)
                        .write(dstSymbolic, value, g, ownership)
                    result.updateInputMapWithAllocatedKeys(dstKeyId, newMap, ownership)
                }
            )
            val srcKeys = keySet.inputSetWithInputElements()
            val srcInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val dstInputKeysCollection = updatedRegion.getInputMapWithInputKeys()

            val adapter = UInputToInputSymbolicRefMapMergeAdapter(srcSymbolic, dstSymbolic, srcKeys)
            val updatedDstCollection = dstInputKeysCollection.copyRange(srcInputKeysCollection, adapter, guard)
            updatedRegion.updateInputMapWithInputKeys(updatedDstCollection)
        },
    )

    private inline fun <R, DstKeyId> mergeInputMapAllocatedKeys(
        initial: R,
        srcMapRef: UHeapRef,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        read: (UInputRefMapWithAllocatedKeysId<MapType, ValueSort>) -> UExpr<ValueSort>,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R
    ) = mergeAllocatedKeys(
        initial,
        inputMapWithAllocatedKeys.keys.toList(),
        guard,
        keySet,
        srcMapRef,
        { it.keyAddress },
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, DstKeyId> mergeAllocatedMapAllocatedKeys(
        initial: R,
        srcMapRef: UConcreteHeapRef,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        read: (UAllocatedRefMapWithAllocatedKeysId) -> UExpr<ValueSort>,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R
    ) = mergeAllocatedKeys(
        initial,
        allocatedMapWithAllocatedKeys.keys.filterTo(mutableListOf()) { it.mapAddress == srcMapRef.address },
        guard,
        keySet,
        srcMapRef,
        { it.keyAddress },
        read,
        mkDstKeyId,
        write
    )

    private inline fun <R, SrcKeyId, DstKeyId> mergeAllocatedKeys(
        initial: R,
        keys: List<SrcKeyId>,
        guard: UBoolExpr,
        keySet: URefSetRegion<MapType>,
        srcMapRef: UHeapRef,
        srcKeyConcreteAddress: (SrcKeyId) -> UConcreteHeapAddress,
        read: (SrcKeyId) -> UExpr<ValueSort>,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UExpr<ValueSort>, UBoolExpr) -> R
    ): R = keys.fold(initial) { result, srcKeyId ->
        val srcKeyAddress = srcKeyConcreteAddress(srcKeyId)
        val srcValue = read(srcKeyId)

        val keyRef = guard.uctx.mkConcreteHeapRef(srcKeyAddress)
        val srcContains = keySet.read(URefSetEntryLValue(srcMapRef, keyRef, mapType))
        val mergedGuard = guard.uctx.mkAnd(srcContains, guard)

        write(result, mkDstKeyId(srcKeyAddress), srcValue, mergedGuard)
    }
}
