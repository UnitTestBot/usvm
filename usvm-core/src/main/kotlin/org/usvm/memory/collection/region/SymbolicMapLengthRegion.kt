package org.usvm.memory.collection.region

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.guardedWrite
import org.usvm.memory.collection.id.UInputSymbolicMapLengthId
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.sampleUValue

typealias UInputSymbolicMapLengthCollection<MapType> = USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>

data class USymbolicMapLengthRef<MapType>(override val sort: USizeSort, val ref: UHeapRef, val mapType: MapType) :
    ULValue<USymbolicMapLengthRef<MapType>, USizeSort> {

    override val memoryRegionId: UMemoryRegionId<USymbolicMapLengthRef<MapType>, USizeSort> =
        USymbolicMapLengthsRegionId(sort, mapType)

    override val key: USymbolicMapLengthRef<MapType> = this
}

data class USymbolicMapLengthsRegionId<MapType>(override val sort: USizeSort, val mapType: MapType) :
    UMemoryRegionId<USymbolicMapLengthRef<MapType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<USymbolicMapLengthRef<MapType>, USizeSort> =
        USymbolicMapLengthMemoryRegion()
}

typealias UAllocatedMapLengths = PersistentMap<UConcreteHeapAddress, USizeExpr>
typealias UInputMapLengths<MapType> = USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>

interface USymbolicMapLengthRegion<MapType> : UMemoryRegion<USymbolicMapLengthRef<MapType>, USizeSort>

internal class USymbolicMapLengthMemoryRegion<MapType>(
    private val allocatedLengths: UAllocatedMapLengths = persistentMapOf(),
    private var inputLengths: UInputMapLengths<MapType>? = null
) : USymbolicMapLengthRegion<MapType> {

    private fun readAllocated(address: UConcreteHeapAddress, sort: USizeSort) =
        allocatedLengths[address] ?: sort.sampleUValue() // sampleUValue is important

    private fun updateAllocated(updated: UAllocatedMapLengths) =
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
            { concreteRef -> readAllocated(concreteRef.address, key.sort) },
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
            val newRegion = region.allocatedLengths.guardedWrite(concreteRef.address, value, innerGuard) {
                key.sort.sampleUValue()
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
