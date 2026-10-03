package org.usvm.machine.expr

import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.TsContext

/** Separate from value storage: a deleted property has no value in any sort. */
internal data class DeletedField(val name: String)

// Reading these names after deleting an own property requires Object.prototype lookup.
internal val OBJECT_PROTOTYPE_PROPERTIES = setOf(
    "__defineGetter__",
    "__defineSetter__",
    "__lookupGetter__",
    "__lookupSetter__",
    "__proto__",
    "constructor",
    "hasOwnProperty",
    "isPrototypeOf",
    "propertyIsEnumerable",
    "toLocaleString",
    "toString",
    "valueOf",
)

internal fun TsContext.deletedFieldLValue(
    instance: UHeapRef,
    field: EtsFieldSignature,
): UFieldLValue<DeletedField, UBoolSort> = deletedFieldLValue(instance, field.name)

internal fun TsContext.deletedFieldLValue(
    instance: UHeapRef,
    fieldName: String,
): UFieldLValue<DeletedField, UBoolSort> = UFieldLValue(boolSort, instance, DeletedField(fieldName))
