package org.usvm.collection.field

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

data class UFieldLValue<Field, Sort : USort>(override val sort: Sort, val ref: UHeapRef, val field: Field) :
    ULValue<UFieldLValue<Field, Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<UFieldLValue<Field, Sort>, Sort> =
        UFieldsRegionId(field, sort)

    override val key: UFieldLValue<Field, Sort>
        get() = this
}

data class UFieldsRegionId<Field, Sort : USort>(val field: Field, override val sort: Sort) :
    UMemoryRegionId<UFieldLValue<Field, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UFieldLValue<Field, Sort>, Sort> =
        UFieldsMemoryRegion(sort, field)
}

typealias UInputFields<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>

interface UFieldsRegion<Field, Sort : USort> : UMemoryRegion<UFieldLValue<Field, Sort>, Sort>

internal class UFieldsMemoryRegion<Field, Sort : USort>(
    private val sort: Sort,
    private val field: Field,
    private val allocatedFields: UPersistentHashMap<UConcreteHeapAddress, UExpr<Sort>> = persistentHashMapOf(),
    private var inputFields: UInputFields<Field, Sort>? = null
) : UFieldsRegion<Field, Sort> {

    private fun updateAllocated(updated: UPersistentHashMap<UConcreteHeapAddress, UExpr<Sort>>) =
        UFieldsMemoryRegion(sort, field, updated, inputFields)

    private fun getInputFields(ref: UFieldLValue<Field, Sort>): UInputFields<Field, Sort> {
        if (inputFields == null)
            inputFields = UInputFieldId(ref.field, ref.sort).emptyRegion()
        return inputFields!!
    }

    private fun updateInput(updated: UInputFields<Field, Sort>) =
        UFieldsMemoryRegion(sort, field, allocatedFields, updated)

    override fun read(key: UFieldLValue<Field, Sort>): UExpr<Sort> = key.ref.mapWithStaticAsSymbolic(
        concreteMapper = { concreteRef -> allocatedFields[concreteRef.address] ?: sort.sampleUValue() },
        symbolicMapper = { symbolicRef -> getInputFields(key).read(symbolicRef) }
    )

    override fun write(
        key: UFieldLValue<Field, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<UFieldLValue<Field, Sort>, Sort> = foldHeapRefWithStaticAsSymbolic(
        key.ref,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, innerGuard) ->
            val newRegion = region.allocatedFields.guardedWrite(concreteRef.address, value, innerGuard, ownership) {
                sort.sampleUValue()
            }
            region.updateAllocated(newRegion)
        },
        blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
            val oldRegion = region.getInputFields(key)
            val newRegion = oldRegion.write(symbolicRef, value, innerGuard, ownership)
            region.updateInput(newRegion)
        }
    )
}
