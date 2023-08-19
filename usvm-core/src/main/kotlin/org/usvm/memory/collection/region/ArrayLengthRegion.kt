package org.usvm.memory.collection.region

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
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.id.UInputArrayLengthId
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.sampleUValue
import org.usvm.uctx

data class UArrayLengthRef<ArrayType>(override val sort: USizeSort, val ref: UHeapRef, val arrayType: ArrayType) :
    ULValue<UArrayLengthRef<ArrayType>, USizeSort> {

    override val memoryRegionId: UMemoryRegionId<UArrayLengthRef<ArrayType>, USizeSort> =
        UArrayLengthsRegionId(sort, arrayType)

    override val key: UArrayLengthRef<ArrayType> = this
}

data class UArrayLengthsRegionId<ArrayType>(override val sort: USizeSort, val arrayType: ArrayType) :
    UMemoryRegionId<UArrayLengthRef<ArrayType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort> =
        UArrayLengthsMemoryRegion()
}

typealias UAllocatedArrayLengths = PersistentMap<UConcreteHeapAddress, USizeExpr>
typealias UInputArrayLengths<ArrayType> = USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>

interface UArrayLengthsRegion<ArrayType> : UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort>

internal class UArrayLengthsMemoryRegion<ArrayType>(
    private val allocatedLengths: UAllocatedArrayLengths = persistentMapOf(),
    private var inputLengths: UInputArrayLengths<ArrayType>? = null
) : UArrayLengthsRegion<ArrayType> {

    private fun readAllocated(address: UConcreteHeapAddress, sort: USizeSort) =
        allocatedLengths[address] ?: sort.sampleUValue() // sampleUValue is important

    private fun getInputLength(ref: UArrayLengthRef<ArrayType>): UInputArrayLengths<ArrayType> {
        if (inputLengths == null)
            inputLengths = UInputArrayLengthId(ref.arrayType, ref.sort, null).emptyRegion()
        return inputLengths!!
    }

    override fun read(key: UArrayLengthRef<ArrayType>): USizeExpr =
        key.ref.map(
            { concreteRef -> readAllocated(concreteRef.address, key.sort) },
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
            val newValue = guard.uctx.mkIte(
                innerGuard,
                { value },
                { region.readAllocated(concreteRef.address, key.sort) }
            )
            UArrayLengthsMemoryRegion(
                allocatedLengths = region.allocatedLengths.put(concreteRef.address, newValue),
                region.inputLengths
            )
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputLength(key)
            val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
            UArrayLengthsMemoryRegion(region.allocatedLengths, inputLengths = newRegion)
        }
    )
}
