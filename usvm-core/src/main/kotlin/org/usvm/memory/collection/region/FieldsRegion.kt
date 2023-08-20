package org.usvm.memory.collection.region

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
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.id.UAllocatedFieldId
import org.usvm.memory.collection.id.UInputFieldId
import org.usvm.memory.foldHeapRef
import org.usvm.memory.map
import org.usvm.sampleUValue
import org.usvm.uctx

data class UFieldRef<Field, Sort : USort>(override val sort: Sort, val ref: UHeapRef, val field: Field) :
    ULValue<UFieldRef<Field, Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<UFieldRef<Field, Sort>, Sort> =
        UFieldsRegionId(field, sort)

    override val key: UFieldRef<Field, Sort> = this
}

data class UFieldsRegionId<Field, Sort : USort>(val field: Field, override val sort: Sort) :
    UMemoryRegionId<UFieldRef<Field, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UFieldRef<Field, Sort>, Sort> =
        UFieldsMemoryRegion()
}

typealias UAllocatedFields<Field, Sort> = PersistentMap<UAllocatedFieldId<Field, Sort>, UExpr<Sort>>
typealias UInputFields<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>

interface UFieldsRegion<Field, Sort : USort> : UMemoryRegion<UFieldRef<Field, Sort>, Sort>

internal class UFieldsMemoryRegion<Field, Sort : USort>(
    private val allocatedFields: UAllocatedFields<Field, Sort> = persistentMapOf(),
    private var inputFields: UInputFields<Field, Sort>? = null
) : UFieldsRegion<Field, Sort> {

    private fun readAllocated(address: UConcreteHeapAddress, field: Field, sort: Sort) =
        allocatedFields[UAllocatedFieldId(field, address, sort)] ?: sort.sampleUValue() // sampleUValue is important

    private fun getInputFields(ref: UFieldRef<Field, Sort>): UInputFields<Field, Sort> {
        if (inputFields == null)
            inputFields = UInputFieldId(ref.field, ref.sort).emptyRegion()
        return inputFields!!
    }

    override fun read(key: UFieldRef<Field, Sort>): UExpr<Sort> =
        key.ref.map(
            { concreteRef -> readAllocated(concreteRef.address, key.field, key.sort) },
            { symbolicRef -> getInputFields(key).read(symbolicRef) }
        )

    override fun write(
        key: UFieldRef<Field, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UFieldRef<Field, Sort>, Sort> =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            blockOnConcrete = { region, (concreteRef, innerGuard) ->
                val concreteKey = UAllocatedFieldId(key.field, concreteRef.address, key.sort)
                val newValue = guard.uctx.mkIte(
                        innerGuard,
                        { value },
                        { region.readAllocated(concreteRef.address, key.field, key.sort) }
                )
                UFieldsMemoryRegion(allocatedFields = region.allocatedFields.put(concreteKey, newValue), inputFields)
            },
            blockOnSymbolic = { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputFields(key)
                val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
                UFieldsMemoryRegion(region.allocatedFields, inputFields = newRegion)
            }
        )
}
