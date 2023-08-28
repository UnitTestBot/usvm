package org.usvm.collection.map.length

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.guardedWrite
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.uctx

typealias UInputMapLengthCollection<MapType> = USymbolicCollection<UInputMapLengthId<MapType>, UHeapRef, USizeSort>

data class UMapLengthLValue<MapType>(val ref: UHeapRef, val mapType: MapType) :
    ULValue<UMapLengthLValue<MapType>, USizeSort> {

    override val sort: USizeSort
        get() = ref.uctx.sizeSort

    override val memoryRegionId: UMemoryRegionId<UMapLengthLValue<MapType>, USizeSort> =
        UMapLengthRegionId(sort, mapType)

    override val key: UMapLengthLValue<MapType>
        get() = this
}

data class UMapLengthRegionId<MapType>(override val sort: USizeSort, val mapType: MapType) :
    UMemoryRegionId<UMapLengthLValue<MapType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UMapLengthLValue<MapType>, USizeSort> =
        UMapLengthMemoryRegion()
}

typealias UAllocatedMapLength<MapType> = PersistentMap<UAllocatedMapLengthId<MapType>, USizeExpr>
typealias UInputMapLength<MapType> = USymbolicCollection<UInputMapLengthId<MapType>, UHeapRef, USizeSort>

interface UMapLengthRegion<MapType> : UMemoryRegion<UMapLengthLValue<MapType>, USizeSort>

internal class UMapLengthMemoryRegion<MapType>(
    private val allocatedLengths: UAllocatedMapLength<MapType> = persistentMapOf(),
    private var inputLengths: UInputMapLength<MapType>? = null
) : UMapLengthRegion<MapType> {

    private fun readAllocated(id: UAllocatedMapLengthId<MapType>) =
        allocatedLengths[id] ?: id.defaultValue

    private fun updateAllocated(updated: UAllocatedMapLength<MapType>) =
        UMapLengthMemoryRegion(updated, inputLengths)

    private fun getInputLength(ref: UMapLengthLValue<MapType>): UInputMapLength<MapType> {
        if (inputLengths == null)
            inputLengths = UInputMapLengthId(ref.mapType, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updateInput(updated: UInputMapLength<MapType>) =
        UMapLengthMemoryRegion(allocatedLengths, updated)

    override fun read(key: UMapLengthLValue<MapType>): USizeExpr =
        key.ref.map(
            { concreteRef -> readAllocated(UAllocatedMapLengthId(key.mapType, concreteRef.address, key.sort)) },
            { symbolicRef -> getInputLength(key).read(symbolicRef) }
        )

    override fun write(
        key: UMapLengthLValue<MapType>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ) = foldHeapRef(
        ref = key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val id = UAllocatedMapLengthId(key.mapType, concreteRef.address, key.sort)
            val newRegion = region.allocatedLengths.guardedWrite(id, value, innerGuard) {
                id.defaultValue
            }
            region.updateAllocated(newRegion)
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputLength(key)
            val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
            region.updateInput(newRegion)
        }
    )
}
