package org.usvm.collection.field

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef
import org.usvm.memory.guardedWrite
import org.usvm.memory.map

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
        UFieldsMemoryRegion()
}

typealias UAllocatedFields<Field, Sort> = PersistentMap<UAllocatedFieldId<Field, Sort>, UExpr<Sort>>
typealias UInputFields<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>

interface UFieldsRegion<Field, Sort : USort> : UMemoryRegion<UFieldLValue<Field, Sort>, Sort>

internal class UFieldsMemoryRegion<Field, Sort : USort>(
    private val allocatedFields: UAllocatedFields<Field, Sort> = persistentMapOf(),
    private var inputFields: UInputFields<Field, Sort>? = null
) : UFieldsRegion<Field, Sort> {

    private fun readAllocated(address: UConcreteHeapAddress, field: Field, sort: Sort): UExpr<Sort> {
        val id = UAllocatedFieldId(field, address, sort)
        return allocatedFields[id] ?: id.defaultValue
    }

    private fun updateAllocated(updated: UAllocatedFields<Field, Sort>) =
        UFieldsMemoryRegion(updated, inputFields)

    private fun getInputFields(ref: UFieldLValue<Field, Sort>): UInputFields<Field, Sort> {
        if (inputFields == null)
            inputFields = UInputFieldId(ref.field, ref.sort).emptyRegion()
        return inputFields!!
    }

    private fun updateInput(updated: UInputFields<Field, Sort>) =
        UFieldsMemoryRegion(allocatedFields, updated)

    override fun read(key: UFieldLValue<Field, Sort>): UExpr<Sort> =
        key.ref.map(
            { concreteRef -> readAllocated(concreteRef.address, key.field, key.sort) },
            { symbolicRef -> getInputFields(key).read(symbolicRef) }
        )

    override fun write(
        key: UFieldLValue<Field, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UFieldLValue<Field, Sort>, Sort> =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            blockOnConcrete = { region, (concreteRef, innerGuard) ->
                val concreteKey = UAllocatedFieldId(key.field, concreteRef.address, key.sort)
                val newRegion = region.allocatedFields.guardedWrite(concreteKey, value, innerGuard) {
                    concreteKey.defaultValue
                }
                region.updateAllocated(newRegion)
            },
            blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputFields(key)
                val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
                region.updateInput(newRegion)
            }
        )
}
