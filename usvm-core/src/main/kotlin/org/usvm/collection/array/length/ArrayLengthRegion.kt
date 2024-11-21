package org.usvm.collection.array.length

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

typealias UInputArrayLengths<ArrayType, USizeSort> = USymbolicCollection<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort>

data class UArrayLengthLValue<ArrayType, USizeSort : USort>(
    val ref: UHeapRef,
    val arrayType: ArrayType,
    override val sort: USizeSort,
) : ULValue<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> {
    override val memoryRegionId: UMemoryRegionId<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> =
        UArrayLengthsRegionId(sort, arrayType)

    override val key: UArrayLengthLValue<ArrayType, USizeSort>
        get() = this
}

data class UArrayLengthsRegionId<ArrayType, USizeSort : USort>(override val sort: USizeSort, val arrayType: ArrayType) :
    UMemoryRegionId<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> =
        UArrayLengthsMemoryRegion(sort, arrayType)
}

interface UArrayLengthsRegion<ArrayType, USizeSort : USort> : UMemoryRegion<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort>

internal class UArrayLengthsMemoryRegion<ArrayType, USizeSort : USort>(
    private val sort: USizeSort,
    private val arrayType: ArrayType,
    private val allocatedLengths: UPersistentHashMap<UConcreteHeapAddress, UExpr<USizeSort>> = persistentHashMapOf(),
    private var inputLengths: UInputArrayLengths<ArrayType, USizeSort>? = null
) : UArrayLengthsRegion<ArrayType, USizeSort> {

    private fun updateAllocated(updated: UPersistentHashMap<UConcreteHeapAddress, UExpr<USizeSort>>) =
        UArrayLengthsMemoryRegion(sort, arrayType, updated, inputLengths)

    private fun getInputLength(ref: UArrayLengthLValue<ArrayType, USizeSort>): UInputArrayLengths<ArrayType, USizeSort> {
        if (inputLengths == null)
            inputLengths = UInputArrayLengthId(ref.arrayType, ref.sort).emptyRegion()
        return inputLengths!!
    }

    private fun updatedInput(updated: UInputArrayLengths<ArrayType, USizeSort>) =
        UArrayLengthsMemoryRegion(sort, arrayType, allocatedLengths, updated)

    override fun read(key: UArrayLengthLValue<ArrayType, USizeSort>): UExpr<USizeSort> = key.ref.mapWithStaticAsSymbolic(
        concreteMapper = { concreteRef -> allocatedLengths[concreteRef.address] ?: sort.sampleUValue() },
        symbolicMapper = { symbolicRef -> getInputLength(key).read(symbolicRef) }
    )

    override fun write(
        key: UArrayLengthLValue<ArrayType, USizeSort>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ) = foldHeapRefWithStaticAsSymbolic(
        key.ref,
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
            region.updatedInput(newRegion)
        }
    )
}
