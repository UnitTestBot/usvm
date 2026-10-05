package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USymbolicHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureStaticsInitialized
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.mkFakeValue
import org.usvm.util.EtsHierarchy
import org.usvm.util.TsResolutionResult
import org.usvm.util.createFakeField
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue
import org.usvm.util.resolveEtsField

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleInstanceFieldRef(
    value: EtsInstanceFieldRef,
): UExpr<*>? = with(ctx) {
    val instanceLocal = value.instance

    // Resolve the instance.
    val instance: UHeapRef = run {
        val resolved = resolve(instanceLocal) ?: return null
        if (resolved.isFakeObject()) {
            scope.assert(resolved.getFakeType(scope).refTypeExpr) ?: run {
                logger.warn { "UNSAT after ensuring fake object is ref-typed" }
                return null
            }
            resolved.extractRef(scope)
        } else {
            check(resolved.sort == addressSort) {
                "Expected address sort for instance, got: ${resolved.sort}"
            }
            resolved.asExpr(addressSort)
        }
    }

    // TODO: consider moving this to 'readField'
    // Check for undefined or null property access.
    checkUndefinedOrNullPropertyRead(scope, instance, propertyName = value.field.name) ?: return null

    // Handle reading "length" property.
    if (value.field.name == "length") {
        return readLengthProperty(scope, instanceLocal, instance, options.maxArraySize)
    }

    // Read the field.
    return readField(scope, instanceLocal, instance, value.field, hierarchy)
}

fun TsContext.readField(
    scope: TsStepScope,
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    checkNotFake(instance)

    val resolvedField = resolveEtsField(instanceLocal, field, hierarchy)
    val sort = when (resolvedField) {
        is TsResolutionResult.Empty -> {
            if (field.name !in listOf("i", "LogLevel")) {
                logger.warn { "Field $field not found, creating fake field" }
            }
            // If we didn't find any real fields, let's create a fake one.
            // It is possible due to mistakes in the IR or if the field was added explicitly
            // in the code.
            // Probably, the right behaviour here is to fork the state.
            instance.createFakeField(scope, field.name)
            addressSort
        }

        is TsResolutionResult.Unique -> typeToSort(resolvedField.property.type)

        is TsResolutionResult.Ambiguous -> unresolvedSort
    }

    scope.doWithState {
        // If we accessed some field, we make an assumption that
        // this field should present in the object.
        // That's not true in the common case for TS, but that's the decision we made.
        val auxiliaryType = EtsAuxiliaryType(properties = setOf(field.name))
        // assert is required to update models
        scope.assert(memory.types.evalIsSubtype(instance, auxiliaryType))
    }

    // If the field type is known, we can read it directly.
    if (sort !is TsUnresolvedSort) {
        val lValue = mkFieldLValue(sort, instance, field)
        val value = scope.calcOnState { memory.read(lValue) }
        if (resolvedField is TsResolutionResult.Unique) {
            when (val fieldType = resolvedField.property.type) {
                is EtsStringLiteralType -> {
                    val maxStringLength = scope.calcOnState { maxStringLength }
                    return materializeTypedStringField(
                        scope = scope,
                        value = value.asExpr(addressSort),
                        literal = fieldType.value,
                        maxStringLength = maxStringLength,
                    )
                }

                is EtsStringType -> {
                    val maxStringLength = scope.calcOnState { maxStringLength }
                    return materializeTypedStringField(scope, value.asExpr(addressSort), maxStringLength)
                }
            }
        }

        return value
    }

    // If the field type is unknown, we create a fake object.
    return scope.calcOnState {
        val boolLValue = mkFieldLValue(boolSort, instance, field)
        val fpLValue = mkFieldLValue(fp64Sort, instance, field)
        val refLValue = mkFieldLValue(addressSort, instance, field)

        val bool = memory.read(boolLValue)
        val fp = memory.read(fpLValue)
        val ref = memory.read(refLValue)

        // If a fake object is already created and assigned to the field,
        // there is no need to recreate another one.
        if (ref.isFakeObject()) {
            ref
        } else {
            val fakeObj = mkFakeValue(scope, bool, fp, ref)
            lValuesToAllocatedFakeObjects += refLValue to fakeObj
            memory.write(refLValue, fakeObj, guard = trueExpr)
            fakeObj
        }
    }
}

private fun TsContext.materializeTypedStringField(
    scope: TsStepScope,
    value: UHeapRef,
    maxStringLength: Int,
    literal: String? = null,
): UHeapRef? {
    // A prior write may have supplied an allocated literal or an already materialized string.
    // Keep its original reference so its existing backing array remains authoritative.
    if (value == mkTsNullValue() || value == mkUndefinedValue() || value is UConcreteHeapRef) {
        return value
    }
    if (value is USymbolicHeapRef && scope.calcOnState { value in boundedStringBackingRefs }) {
        return value
    }
    if (literal != null && literal.length > maxStringLength) {
        scope.doWithState {
            terminateAsUnsupported(reason = "Literal string field exceeds configured string length $maxStringLength")
        }
        return null
    }

    val stringRef = scope.calcOnState { makeSymbolicRefUntyped() }
    scope.assert(mkHeapRefEq(stringRef, value)) ?: return null
    scope.assert(mkNot(mkHeapRefEq(stringRef, mkTsNullValue()))) ?: return null
    scope.assert(mkNot(mkHeapRefEq(stringRef, mkUndefinedValue()))) ?: return null
    scope.assert(scope.calcOnState { memory.types.evalTypeEquals(stringRef, EtsStringType) }) ?: return null

    val charsRef = scope.calcOnState {
        val valueLValue = mkStringBackingLValue(stringRef)
        memory.read(valueLValue)
    }
    val charsType = EtsArrayType(EtsNumberType, dimensions = 1)
    scope.assert(mkNot(mkHeapRefEq(charsRef, mkTsNullValue()))) ?: return null
    scope.assert(mkNot(mkHeapRefEq(charsRef, mkUndefinedValue()))) ?: return null
    scope.assert(scope.calcOnState { memory.types.evalTypeEquals(charsRef, charsType) }) ?: return null

    val length = scope.calcOnState { memory.read(mkStringBackingLengthLValue(charsRef)) }
    if (literal == null) {
        val lengthIsNonNegative = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
        val lengthIsWithinLimit = mkBvSignedLessOrEqualExpr(length, mkBv(maxStringLength))
        scope.assert(mkAnd(lengthIsNonNegative, lengthIsWithinLimit)) ?: return null
    } else {
        // A literal type fixes both the UTF-16 length and every code unit.
        scope.assert(mkEq(length, mkBv(literal.length))) ?: return null
        for ((index, character) in literal.withIndex()) {
            val element = scope.calcOnState {
                memory.read(mkStringBackingElementLValue(charsRef, mkBv(index)))
            }
            scope.assert(mkEq(element, mkBv(character.code, bv16Sort))) ?: return null
        }
    }

    scope.doWithState { boundedStringBackingRefs += stringRef }

    return stringRef
}

internal fun TsExprResolver.handleStaticFieldRef(
    value: EtsStaticFieldRef,
): UExpr<*>? = with(ctx) {
    return readStaticField(scope, value.field, hierarchy)
}

fun TsContext.readStaticField(
    scope: TsStepScope,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    // TODO: handle unresolved class, or multiple classes
    val clazz = scene.projectAndSdkClasses.singleOrNull {
        it.signature == field.enclosingClass
    } ?: return null

    // Initialize statics in `clazz` if necessary.
    ensureStaticsInitialized(scope, clazz) ?: return null

    // Get the static instance.
    val instance = scope.calcOnState { getStaticInstance(clazz) }

    // Read the field.
    return readField(scope, null, instance, field, hierarchy)
}
