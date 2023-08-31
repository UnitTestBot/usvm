package org.usvm.collection.array.length

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef
import org.usvm.memory.guardedWrite
import org.usvm.memory.map
import org.usvm.sampleUValue
import org.usvm.uctx

data class UArrayLengthLValue<ArrayType>(val ref: UHeapRef, val arrayType: ArrayType) :
    ULValue<UArrayLengthLValue<ArrayType>, USizeSort> {

    override val sort: USizeSort
        get() = ref.uctx.sizeSort

    override val memoryRegionId: UMemoryRegionId<UArrayLengthLValue<ArrayType>, USizeSort> =
        UArrayLengthsRegionId(sort, arrayType)

    override val key: UArrayLengthLValue<ArrayType>
        get() = this
}

data class UArrayLengthsRegionId<ArrayType>(override val sort: USizeSort, val arrayType: ArrayType) :
    UMemoryRegionId<UArrayLengthLValue<ArrayType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UArrayLengthLValue<ArrayType>, USizeSort> =
        UArrayLengthsMemoryRegion(sort, arrayType)
}

typealias UInputArrayLengths<ArrayType> = USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>

interface UArrayLengthsRegion<ArrayType> : UMemoryRegion<UArrayLengthLValue<ArrayType>, USizeSort>

internal class UArrayLengthsMemoryRegion<ArrayType>(
    private val sort: USizeSort,
    private val arrayType: ArrayType,
    private val allocatedLengths: PersistentMap<UConcreteHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputLengths: UInputArrayLengths<ArrayType>? = null
) : UArrayLengthsRegion<ArrayType> {

    private fun updateAllocated(updated: PersistentMap<UConcreteHeapAddress, USizeExpr>) =
        UArrayLengthsMemoryRegion(sort, arrayType, updated, inputLengths)

    private fun getInputLength(ref: UArrayLengthLValue<ArrayType>): UInputArrayLengths<ArrayType> {
        if (inputLengths == null)
            inputLengths = UInputArrayLengthId(ref.arrayType, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updatedInput(updated: UInputArrayLengths<ArrayType>) =
        UArrayLengthsMemoryRegion(sort, arrayType, allocatedLengths, updated)

    override fun read(key: UArrayLengthLValue<ArrayType>): USizeExpr =
        key.ref.map(
            { concreteRef -> allocatedLengths[concreteRef.address] ?: sort.sampleUValue() },
            { symbolicRef -> getInputLength(key).read(symbolicRef) }
        )

    override fun write(
        key: UArrayLengthLValue<ArrayType>,
        value: USizeExpr,
        guard: UBoolExpr
    ) = foldHeapRef(
        key.ref,
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
            region.updatedInput(newRegion)
        }
    )
}
