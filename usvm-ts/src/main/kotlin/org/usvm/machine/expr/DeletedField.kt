package org.usvm.machine.expr

import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.TsContext

/** Separate from value storage: a deleted property has no value in any sort. */
internal data class DeletedField(val name: String)

internal fun TsContext.deletedFieldLValue(
    instance: UHeapRef,
    field: EtsFieldSignature,
): UFieldLValue<DeletedField, UBoolSort> = UFieldLValue(boolSort, instance, DeletedField(field.name))
