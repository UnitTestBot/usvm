package org.usvm.collection.map.length

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.guardedWrite
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.sampleUValue

typealias UInputMapLengthCollection<MapType, USizeSort> = USymbolicCollection<UInputMapLengthId<MapType, USizeSort>, UHeapRef, USizeSort>
typealias UInputMapLength<MapType, USizeSort> = USymbolicCollection<UInputMapLengthId<MapType, USizeSort>, UHeapRef, USizeSort>

data class UMapLengthLValue<MapType, USizeSort : USort>(
    val ref: UHeapRef,
    val mapType: MapType,
    override val sort: USizeSort,
) : ULValue<UMapLengthLValue<MapType, USizeSort>, USizeSort> {
    override val memoryRegionId: UMemoryRegionId<UMapLengthLValue<MapType, USizeSort>, USizeSort> =
        UMapLengthRegionId(sort, mapType)

    override val key: UMapLengthLValue<MapType, USizeSort>
        get() = this
}

data class UMapLengthRegionId<MapType, USizeSort : USort>(override val sort: USizeSort, val mapType: MapType) :
    UMemoryRegionId<UMapLengthLValue<MapType, USizeSort>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UMapLengthLValue<MapType, USizeSort>, USizeSort> =
        UMapLengthMemoryRegion(sort, mapType)
}

interface UMapLengthRegion<MapType, USizeSort : USort> : UMemoryRegion<UMapLengthLValue<MapType, USizeSort>, USizeSort>

internal class UMapLengthMemoryRegion<MapType, USizeSort : USort>(
    private val sort: USizeSort,
    private val mapType: MapType,
    private val allocatedLengths: UPersistentHashMap<UConcreteHeapAddress, UExpr<USizeSort>> = persistentHashMapOf(),
    private var inputLengths: UInputMapLength<MapType, USizeSort>? = null
) : UMapLengthRegion<MapType, USizeSort> {

    private fun updateAllocated(updated: UPersistentHashMap<UConcreteHeapAddress, UExpr<USizeSort>>) =
        UMapLengthMemoryRegion(sort, mapType, updated, inputLengths)

    private fun getInputLength(ref: UMapLengthLValue<MapType, USizeSort>): UInputMapLength<MapType, USizeSort> {
        if (inputLengths == null)
            inputLengths = UInputMapLengthId(ref.mapType, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updateInput(updated: UInputMapLength<MapType, USizeSort>) =
        UMapLengthMemoryRegion(sort, mapType, allocatedLengths, updated)

    override fun read(key: UMapLengthLValue<MapType, USizeSort>): UExpr<USizeSort> = key.ref.mapWithStaticAsSymbolic(
        concreteMapper = { concreteRef -> allocatedLengths[concreteRef.address] ?: sort.sampleUValue() },
        symbolicMapper = { symbolicRef -> getInputLength(key).read(symbolicRef) }
    )

    override fun write(
        key: UMapLengthLValue<MapType, USizeSort>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRefWithStaticAsSymbolic(
        ref = key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val newRegion = region.allocatedLengths.guardedWrite(concreteRef.address, value, innerGuard, ownership) {
                sort.sampleUValue()
            }
            region.updateAllocated(newRegion)
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputLength(key)
            val newRegion = oldRegion.write(symbolicRef, value, innerGuard, ownership)
            region.updateInput(newRegion)
        }
    )
}
