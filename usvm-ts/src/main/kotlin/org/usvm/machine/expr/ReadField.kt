package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassType
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
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureStaticsInitialized
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.machine.types.mkFakeValue
import org.usvm.types.TypesResult
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
    return resolveField(scope, instanceLocal, instance, value.field, hierarchy)
}

private fun TsContext.resolveField(
    scope: TsStepScope,
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    checkNotFake(instance)

    val deleted = scope.calcOnState { memory.read(deletedFieldLValue(instance, field)) }
    if (deleted.isTrue) return mkUndefinedValue()

    val wasWritten = isAllocatedConcreteHeapRef(instance) &&
        scope.calcOnState { (instance to field.name) in writtenConcreteFields }
    val objectClass = if (isAllocatedConcreteHeapRef(instance)) {
        val types = scope.calcOnState { memory.typeStreamOf(instance).take(n = 2) }
        val type = (types as? TypesResult.SuccessfulTypesResult)?.types?.singleOrNull() as? EtsClassType
        type?.let { hierarchy.classesForType(it).singleOrNull() }
            ?.takeIf { it.category == EtsClassCategory.OBJECT }
    } else {
        null
    }
    if (isAllocatedConcreteHeapRef(instance) && !wasWritten) {
        if (objectClass != null &&
            objectClass.fields.none { it.name == field.name } &&
            objectClass.methods.none { it.name == field.name }
        ) {
            val prototypeWasAssigned = scope.calcOnState { (instance to "__proto__") in writtenConcreteFields }
            if (objectClass.fields.any { it.name == "__proto__" } ||
                prototypeWasAssigned || field.name in OBJECT_PROTOTYPE_PROPERTIES
            ) {
                throw UnsupportedOperationException("Reading '${field.name}' requires unsupported prototype lookup")
            }

            // This object literal has neither an initial own field nor a later write.
            return mkUndefinedValue()
        }
    }

    val writtenObjectLiteralSort = if (wasWritten) {
        scope.calcOnState { writtenObjectLiteralFieldSorts[instance to field.name] }
    } else {
        null
    }
    val declaredObjectLiteralSort = objectClass?.fields
        ?.singleOrNull { it.name == field.name }
        ?.let { typeToSort(it.type) }
    val resolvedField = resolveEtsField(instanceLocal, field, hierarchy)
    val sort = writtenObjectLiteralSort ?: declaredObjectLiteralSort
        ?: when (resolvedField) {
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

    if (!wasWritten) {
        val fieldExists = scope.calcOnState {
            val auxiliaryType = EtsAuxiliaryType(properties = setOf(field.name))
            memory.types.evalIsSubtype(instance, auxiliaryType)
        }
        scope.assert(fieldExists) ?: return null
    }

    val value = readField(scope, instance, field, sort)
    val materializedValue = if (resolvedField !is TsResolutionResult.Unique || sort is TsUnresolvedSort) {
        value
    } else {
        val maxStringLength = scope.calcOnState { maxStringLength }
        when (val fieldType = resolvedField.property.type) {
            is EtsStringLiteralType -> materializeTypedStringField(
                scope = scope,
                value = value.asExpr(addressSort),
                literal = fieldType.value,
                maxStringLength = maxStringLength,
            )

            is EtsStringType -> materializeTypedStringField(scope, value.asExpr(addressSort), maxStringLength)
            else -> value
        } ?: return null
    }

    if (deleted.isFalse) return materializedValue

    return iteWriteIntoFakeObject(
        scope = scope,
        condition = deleted,
        trueBranchValue = mkUndefinedValue(),
        falseBranchValue = materializedValue,
    )
}

/** Reading a field always produces a value; path validation belongs to [resolveField]. */
private fun TsContext.readField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
    sort: USort,
): UExpr<*> {
    if (sort !is TsUnresolvedSort) {
        val lValue = mkFieldLValue(sort, instance, field)
        return scope.calcOnState { memory.read(lValue) }
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
    val constraints = scope.calcOnState {
        val charsRef = memory.read(mkStringBackingLValue(stringRef))
        val charsType = EtsArrayType(EtsNumberType, dimensions = 1)
        val length = memory.read(mkStringBackingLengthLValue(charsRef))
        val stringType = memory.types.evalTypeEquals(stringRef, EtsStringType)
        val backingType = memory.types.evalTypeEquals(charsRef, charsType)
        val contents = if (literal == null) {
            val nonnegativeLength = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
            val boundedLength = mkBvSignedLessOrEqualExpr(length, mkBv(maxStringLength))
            mkAnd(nonnegativeLength, boundedLength)
        } else {
            // A literal type fixes both the UTF-16 length and every code unit.
            val characters = literal.mapIndexed { index, character ->
                val element = memory.read(mkStringBackingElementLValue(charsRef, mkBv(index)))
                mkEq(element, mkBv(character.code, bv16Sort))
            }
            mkAnd(mkEq(length, mkBv(literal.length)), mkAnd(characters))
        }

        mkAnd(
            mkHeapRefEq(stringRef, value),
            mkNotNullOrUndefined(stringRef),
            stringType,
            mkNotNullOrUndefined(charsRef),
            backingType,
            contents,
        )
    }
    scope.assert(constraints) ?: return null

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
    return resolveField(scope, null, instance, field, hierarchy)
}
