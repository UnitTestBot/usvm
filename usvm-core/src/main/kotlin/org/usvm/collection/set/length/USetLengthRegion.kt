package org.usvm.collection.set.length

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.UAnySetRegionId
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.guardedWrite
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.sampleUValue

typealias UInputSetLengthCollection<SetType, USizeSort> = USymbolicCollection<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort>
typealias UInputSetLength<SetType, USizeSort> = USymbolicCollection<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort>

data class USetLengthLValue<SetType, USizeSort : USort>(
    val ref: UHeapRef,
    val setId: UAnySetRegionId<SetType, *>,
    override val sort: USizeSort,
) : ULValue<USetLengthLValue<SetType, USizeSort>, USizeSort> {
    override val memoryRegionId: UMemoryRegionId<USetLengthLValue<SetType, USizeSort>, USizeSort> =
        USetLengthRegionId(sort, setId)

    override val key: USetLengthLValue<SetType, USizeSort>
        get() = this
}

data class USetLengthRegionId<SetType, USizeSort : USort>(
    override val sort: USizeSort,
    val setId: UAnySetRegionId<SetType, *>
) : UMemoryRegionId<USetLengthLValue<SetType, USizeSort>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<USetLengthLValue<SetType, USizeSort>, USizeSort> =
        USetLengthMemoryRegion(sort, setId)
}

interface USetLengthRegion<MapType, USizeSort : USort> : UMemoryRegion<USetLengthLValue<MapType, USizeSort>, USizeSort>

internal class USetLengthMemoryRegion<SetType, USizeSort : USort>(
    private val sort: USizeSort,
    private val setId: UAnySetRegionId<SetType, *>,
    private val allocatedLengths: PersistentMap<UConcreteHeapAddress, UExpr<USizeSort>> = persistentHashMapOf(),
    private var inputLengths: UInputSetLength<SetType, USizeSort>? = null
) : USetLengthRegion<SetType, USizeSort> {

    private fun updateAllocated(updated: PersistentMap<UConcreteHeapAddress, UExpr<USizeSort>>) =
        USetLengthMemoryRegion(sort, setId, updated, inputLengths)

    private fun getInputLength(ref: USetLengthLValue<SetType, USizeSort>): UInputSetLength<SetType, USizeSort> {
        if (inputLengths == null)
            inputLengths = UInputSetLengthId(ref.setId, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updateInput(updated: UInputSetLength<SetType, USizeSort>) =
        USetLengthMemoryRegion(sort, setId, allocatedLengths, updated)

    override fun read(key: USetLengthLValue<SetType, USizeSort>): UExpr<USizeSort> = key.ref.mapWithStaticAsSymbolic(
        concreteMapper = { concreteRef -> allocatedLengths[concreteRef.address] ?: sort.sampleUValue() },
        symbolicMapper = { symbolicRef -> getInputLength(key).read(symbolicRef) }
    )

    override fun write(
        key: USetLengthLValue<SetType, USizeSort>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ) = foldHeapRefWithStaticAsSymbolic(
        ref = key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val newRegion = region.allocatedLengths.guardedWrite(concreteRef.address, value, innerGuard) {
                sort.sampleUValue()
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
