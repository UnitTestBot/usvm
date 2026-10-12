package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassType
import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.types.singleOrNull
import org.usvm.util.EtsHierarchy

internal const val PROTOTYPE_PROPERTY_NAME = "__proto__"

// Reading these names after deleting an own property requires Object.prototype lookup.
internal val OBJECT_PROTOTYPE_PROPERTIES = setOf(
    "__defineGetter__",
    "__defineSetter__",
    "__lookupGetter__",
    "__lookupSetter__",
    PROTOTYPE_PROPERTY_NAME,
    "constructor",
    "hasOwnProperty",
    "isPrototypeOf",
    "propertyIsEnumerable",
    "toLocaleString",
    "toString",
    "valueOf",
)

/** Allocated object literals retain their own declaration, independently of the local's widened type. */
internal fun TsContext.objectLiteralClass(
    scope: TsStepScope,
    instance: UHeapRef,
    hierarchy: EtsHierarchy,
): EtsClass? {
    if (!isAllocatedConcreteHeapRef(instance)) return null

    val type = scope.calcOnState { memory.typeStreamOf(instance).singleOrNull() } as? EtsClassType ?: return null
    return hierarchy.classesForType(type).singleOrNull()?.takeIf { it.category == EtsClassCategory.OBJECT }
}

internal fun EtsClass.hasOwnProperty(name: String): Boolean =
    fields.any { it.name == name } || methods.any { it.name == name }

private fun TsStepScope.hasPrototypeMutation(instance: UHeapRef, clazz: EtsClass): Boolean = calcOnState {
    clazz.fields.any { it.name == PROTOTYPE_PROPERTY_NAME } ||
        (instance to PROTOTYPE_PROPERTY_NAME) in writtenConcreteFields
}

internal fun TsStepScope.ensureNoPrototypeMutation(instance: UHeapRef, clazz: EtsClass) {
    // EtsIR records both { __proto__: value } and later assignments as ordinary fields.
    if (hasPrototypeMutation(instance, clazz)) {
        throw UnsupportedOperationException("Object literal prototype mutation in 'in' is not supported")
    }
}

internal fun TsStepScope.ensureMissingPropertyHasNoPrototype(instance: UHeapRef, clazz: EtsClass, name: String) {
    if (hasPrototypeMutation(instance, clazz) || name in OBJECT_PROTOTYPE_PROPERTIES) {
        throw UnsupportedOperationException("Reading '$name' requires unsupported prototype lookup")
    }
}

internal fun ensureOwnPropertyLookup(name: String, hasOwnProperty: Boolean, deleted: UBoolExpr) {
    val ownPropertyMayBeMissing = !hasOwnProperty || !deleted.isFalse
    val requiresPrototypeLookup = name == PROTOTYPE_PROPERTY_NAME ||
        name in OBJECT_PROTOTYPE_PROPERTIES && ownPropertyMayBeMissing
    if (requiresPrototypeLookup) {
        throw UnsupportedOperationException("Prototype lookup for '$name' in 'in' is not supported")
    }
}
