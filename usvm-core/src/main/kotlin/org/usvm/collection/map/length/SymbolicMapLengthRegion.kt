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

typealias UInputSymbolicMapLengthCollection<MapType> = USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>

data class USymbolicMapLengthRef<MapType>(val ref: UHeapRef, val mapType: MapType) :
    ULValue<USymbolicMapLengthRef<MapType>, USizeSort> {

    override val sort: USizeSort
        get() = ref.uctx.sizeSort

    override val memoryRegionId: UMemoryRegionId<USymbolicMapLengthRef<MapType>, USizeSort> =
        USymbolicMapLengthsRegionId(sort, mapType)

    override val key: USymbolicMapLengthRef<MapType>
        get() = this
}

data class USymbolicMapLengthsRegionId<MapType>(override val sort: USizeSort, val mapType: MapType) :
    UMemoryRegionId<USymbolicMapLengthRef<MapType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<USymbolicMapLengthRef<MapType>, USizeSort> =
        USymbolicMapLengthMemoryRegion()
}

typealias UAllocatedMapLengths<MapType> = PersistentMap<UAllocatedSymbolicMapLengthId<MapType>, USizeExpr>
typealias UInputMapLengths<MapType> = USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>

interface USymbolicMapLengthRegion<MapType> : UMemoryRegion<USymbolicMapLengthRef<MapType>, USizeSort>

internal class USymbolicMapLengthMemoryRegion<MapType>(
    private val allocatedLengths: UAllocatedMapLengths<MapType> = persistentMapOf(),
    private var inputLengths: UInputMapLengths<MapType>? = null
) : USymbolicMapLengthRegion<MapType> {

    private fun readAllocated(id: UAllocatedSymbolicMapLengthId<MapType>) =
        allocatedLengths[id] ?: id.defaultValue

    private fun updateAllocated(updated: UAllocatedMapLengths<MapType>) =
        USymbolicMapLengthMemoryRegion(updated, inputLengths)

    private fun getInputLength(ref: USymbolicMapLengthRef<MapType>): UInputMapLengths<MapType> {
        if (inputLengths == null)
            inputLengths = UInputSymbolicMapLengthId(ref.mapType, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updateInput(updated: UInputMapLengths<MapType>) =
        USymbolicMapLengthMemoryRegion(allocatedLengths, updated)

    override fun read(key: USymbolicMapLengthRef<MapType>): USizeExpr =
        key.ref.map(
            { concreteRef -> readAllocated(UAllocatedSymbolicMapLengthId(key.mapType, concreteRef.address, key.sort)) },
            { symbolicRef -> getInputLength(key).read(symbolicRef) }
        )

    override fun write(
        key: USymbolicMapLengthRef<MapType>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ) = foldHeapRef(
        ref = key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val id = UAllocatedSymbolicMapLengthId(key.mapType, concreteRef.address, key.sort)
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
