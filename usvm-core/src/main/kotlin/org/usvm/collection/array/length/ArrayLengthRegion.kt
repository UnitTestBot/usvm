package org.usvm.collection.array.length

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
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
import org.usvm.uctx

data class UArrayLengthRef<ArrayType>(val ref: UHeapRef, val arrayType: ArrayType) :
    ULValue<UArrayLengthRef<ArrayType>, USizeSort> {

    override val sort: USizeSort
        get() = ref.uctx.sizeSort

    override val memoryRegionId: UMemoryRegionId<UArrayLengthRef<ArrayType>, USizeSort> =
        UArrayLengthsRegionId(sort, arrayType)

    override val key: UArrayLengthRef<ArrayType>
        get() = this
}

data class UArrayLengthsRegionId<ArrayType>(override val sort: USizeSort, val arrayType: ArrayType) :
    UMemoryRegionId<UArrayLengthRef<ArrayType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort> =
        UArrayLengthsMemoryRegion()
}

typealias UAllocatedArrayLengths<ArrayType> = PersistentMap<UAllocatedArrayLengthId<ArrayType>, USizeExpr>
typealias UInputArrayLengths<ArrayType> = USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>

interface UArrayLengthsRegion<ArrayType> : UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort>

internal class UArrayLengthsMemoryRegion<ArrayType>(
    private val allocatedLengths: UAllocatedArrayLengths<ArrayType> = persistentMapOf(),
    private var inputLengths: UInputArrayLengths<ArrayType>? = null
) : UArrayLengthsRegion<ArrayType> {

    private fun readAllocated(id: UAllocatedArrayLengthId<ArrayType>) =
        allocatedLengths[id] ?: id.defaultValue

    private fun updateAllocated(updated: UAllocatedArrayLengths<ArrayType>) =
        UArrayLengthsMemoryRegion(updated, inputLengths)

    private fun getInputLength(ref: UArrayLengthRef<ArrayType>): UInputArrayLengths<ArrayType> {
        if (inputLengths == null)
            inputLengths = UInputArrayLengthId(ref.arrayType, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updatedInput(updated: UInputArrayLengths<ArrayType>) =
        UArrayLengthsMemoryRegion(allocatedLengths, updated)

    override fun read(key: UArrayLengthRef<ArrayType>): USizeExpr =
        key.ref.map(
            { concreteRef -> readAllocated(UAllocatedArrayLengthId(key.arrayType, concreteRef.address, key.sort)) },
            { symbolicRef -> getInputLength(key).read(symbolicRef) }
        )

    override fun write(
        key: UArrayLengthRef<ArrayType>,
        value: USizeExpr,
        guard: UBoolExpr
    ) = foldHeapRef(
        key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val id = UAllocatedArrayLengthId(key.arrayType, concreteRef.address, key.sort)
            val newRegion = region.allocatedLengths.guardedWrite(id, value, innerGuard) {
                id.defaultValue
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
