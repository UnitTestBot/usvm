package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureStaticsInitialized
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.machine.types.iteUnresolvedValue
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.requiresConstructorWitness
import org.usvm.machine.types.toAuxiliaryType
import org.usvm.util.EtsHierarchy
import org.usvm.util.InputPropertyDeclaration
import org.usvm.util.TsResolutionResult
import org.usvm.util.arrayStorageType
import org.usvm.util.createFakeField
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue
import org.usvm.util.resolveEtsField
import org.usvm.util.boxArrayValue as boxRuntimeValue

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleInstanceFieldRef(
    value: EtsInstanceFieldRef,
): UExpr<*>? = with(ctx) {
    when (val captured = tryCaptureInstanceCall(value)) {
        is TsExprApproximationResult.SuccessfulApproximation -> return captured.expr
        TsExprApproximationResult.ResolveFailure -> return null
        TsExprApproximationResult.NoApproximation -> {}
    }

    val instanceLocal = value.instance

    // Resolve the instance.
    val rawInstance: UHeapRef = run {
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
    checkUndefinedOrNullPropertyRead(scope, rawInstance, propertyName = value.field.name) ?: return null
    val instance = resolveHeapRef(scope, rawInstance) ?: return null
    if (!ensureSupportedArrayPrototypeAccess(instance, value.field.name)) return null
    ensureSupportedDatePrototypeAccess(instance, value.field.name)

    val iteratorField = readBuiltinIteratorField(instance, value.field.name)
    if (iteratorField != null) return iteratorField

    // Handle reading "length" property.
    val storageType = scope.calcOnState { arrayStorageType(instance, instanceLocal.type) }
    val usesArrayLength = storageType is EtsArrayType || storageType == EtsStringType ||
        storageType == EtsAnyType || storageType == EtsUnknownType
    val nativeTypedArray = storageType is EtsClassType &&
        storageType.signature.file == EtsClassSignature.UNKNOWN.file &&
        storageType.signature.name in NATIVE_TYPED_ARRAY_NAMES
    if (value.field.name == "length" && (usesArrayLength || nativeTypedArray)) {
        val isObjectProperty = storageType is EtsClassType || storageType is EtsUnclearRefType ||
            scope.calcOnState { trackedObjectProperties.any { it.instance == instance && it.name == "length" } }
        if (!isObjectProperty) {
            return readLengthProperty(
                scope = scope,
                instanceLocal = instanceLocal,
                instance = instance,
                maxArraySize = options.maxArraySize,
                onFeatureLimitation = ::reportRuntimeFeatureLimitation,
            )
        }
    }

    // Read the field.
    return resolveField(scope, instanceLocal, instance, value.field, hierarchy)
}

internal fun TsContext.resolveField(
    scope: TsStepScope,
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    checkNotFake(instance)
    val receiver = resolveHeapRef(scope, instance) ?: return null
    val intrinsicRegExp = receiver is UConcreteHeapRef && scope.calcOnState { receiver in regularExpressions }
    if (intrinsicRegExp) return readRegExpField(scope, receiver as UConcreteHeapRef, field.name)

    if (isDateModelTimestamp(scope = scope, instance = receiver, field = field)) {
        val timestamp = dateModelTimestampLValue(receiver)
        return scope.calcOnState { memory.read(timestamp) }
    }

    // Input fields have symbolic initial presence. Allocations use their declaration and later writes.
    return if (!isAllocatedConcreteHeapRef(receiver) && instanceLocal?.type !is EtsArrayType) {
        resolveInputField(scope, instanceLocal, receiver, field, hierarchy)
    } else {
        resolveDeclaredField(scope, instanceLocal, receiver, field, hierarchy)
    }
}

private fun TsContext.resolveDeclaredField(
    scope: TsStepScope,
    local: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    val dateConstructor = readIntrinsicDateConstructor(scope, instance, field.name)
    if (dateConstructor != null) return dateConstructor

    val deleted = scope.calcOnState { memory.read(deletedFieldLValue(instance, field)) }
    if (deleted.isTrue) return mkUndefinedValue()

    val modeledValue = readModeledErrorField(scope, instance, field)
    if (modeledValue != null) return applyFieldDeletion(scope, deleted, modeledValue)

    val resolvedField = resolveEtsField(local, field.runtimeFieldSignature(), hierarchy)

    // A literal's own declarations, rather than scene-wide same-name fields, determine its initial storage.
    val objectClass = objectLiteralClass(scope, instance, hierarchy)
    val wasWritten = scope.calcOnState { (instance to field.name) in writtenConcreteFields }
    if (objectClass != null && !wasWritten && !objectClass.hasOwnProperty(field.name)) {
        scope.ensureMissingPropertyHasNoPrototype(instance, objectClass, field.name)
        return mkUndefinedValue()
    }

    val declaredLiteralField = objectClass?.fields?.singleOrNull { it.name == field.name }
    val writtenSort = scope.calcOnState { writtenObjectLiteralFieldSorts[instance to field.name] }
    val declaredLiteralSort = declaredLiteralField?.let { typeToSort(it.type) }
    val storageSort = writtenSort ?: declaredLiteralSort ?: resolvedFieldSort(
        scope = scope,
        instance = instance,
        field = field,
        resolved = resolvedField,
        hierarchy = hierarchy,
    )
    val declaredType = when {
        objectClass != null && writtenSort != null -> null
        objectClass != null -> declaredLiteralField?.type
        else -> (resolvedField as? TsResolutionResult.Unique)?.property?.type
    }

    val requiresDeclaredPresence = !wasWritten
    if (requiresDeclaredPresence) {
        val fieldExists = scope.calcOnState {
            memory.types.evalIsSubtype(instance, EtsAuxiliaryType(properties = setOf(field.name)))
        }
        scope.assert(fieldExists) ?: return null
    }

    val storedValue = readField(scope, instance, field, storageSort)
    val value = materializeFieldValue(scope, storedValue, storageSort, declaredType) ?: return null
    return applyFieldDeletion(scope, deleted, value)
}

private fun TsContext.applyFieldDeletion(scope: TsStepScope, deleted: UBoolExpr, value: UExpr<*>): UExpr<*> {
    if (deleted.isFalse) return value

    // Conditional deletion yields undefined on the deleted branch, while preserving the stored runtime kind.
    return iteWriteIntoFakeObject(
        scope = scope,
        condition = deleted,
        trueBranchValue = mkUndefinedValue(),
        falseBranchValue = value,
    )
}

private fun TsContext.resolvedFieldSort(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
    resolved: TsResolutionResult<EtsField>,
    hierarchy: EtsHierarchy,
): USort = when (resolved) {
    is TsResolutionResult.Unique -> typeToSort(resolved.property.type)
    is TsResolutionResult.Ambiguous -> resolveAmbiguousFieldSort(
        scope = scope,
        instance = instance,
        fields = resolved.properties,
        hierarchy = hierarchy,
    )
    TsResolutionResult.Empty -> {
        if (field.name !in listOf("i", "LogLevel")) {
            logger.warn { "Field $field not found, creating fake field" }
        }
        instance.createFakeField(scope, field.name)
        addressSort
    }
}

private fun TsContext.materializeFieldValue(
    scope: TsStepScope,
    value: UExpr<*>,
    sort: USort,
    type: EtsType?,
): UExpr<*>? {
    if (sort is TsUnresolvedSort || (type !is EtsStringType && type !is EtsStringLiteralType)) return value

    return materializeTypedStringField(
        scope = scope,
        value = value.asExpr(addressSort),
        maxStringLength = scope.calcOnState { maxStringLength },
        literal = (type as? EtsStringLiteralType)?.value,
    )
}

private fun TsContext.resolveInputField(
    scope: TsStepScope,
    local: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    val initialPresence = trackInputProperty(scope, instance, local, field.name, hierarchy) ?: return null
    val present = inputPropertyPresence(scope, instance, field.name, initialPresence)
    val written = scope.calcOnState { memory.read(writtenPropertyLValue(instance, field.name)) }
    val activeInitial = mkAnd(present, mkNot(written))

    // Annotation constraints apply only while the initial field value is visible, before a write or deletion.
    val declared = declaredInputProperty(local, field.name, hierarchy)
    val rawInitial = scope.calcOnState { readInputPropertyValue(memory, instance, field.name, written = false) }
    val initial = prepareInitialInputField(scope, rawInitial, declared, activeInitial, hierarchy) ?: return null
    val current = scope.calcOnState { currentInputPropertyValue(memory, instance, field.name, initial) }

    // Missing and present-but-undefined are distinct states; both read as an undefined reference value.
    val undefined = current.copy(refValue = mkUndefinedValue(), type = EtsFakeType.mkRef(this))
    val value = iteUnresolvedValue(present, current, undefined)
    return scope.calcOnState { mkFakeValue(scope, value) }
}

private fun TsContext.prepareInitialInputField(
    scope: TsStepScope,
    value: TsUnresolvedValue,
    declaration: InputPropertyDeclaration?,
    activeInitial: UBoolExpr,
    hierarchy: EtsHierarchy,
): TsUnresolvedValue? {
    // A write, deletion or absent property can make the declared initial value irrelevant.
    // Do not reject the entire input object just because another field needs a constructor witness.
    val unsupportedInitial = when {
        declaration?.type?.requiresConstructorWitness() == true -> "Constructor-typed input field"
        declaration?.type is EtsGenericType -> "Unresolved generic input field"
        else -> null
    }
    if (unsupportedInitial != null) {
        scope.fork(mkNot(activeInitial), blockOnFalseState = {
            terminateAsUnsupported(reason = "$unsupportedInitial is not modeled")
        }) ?: return null
        return value
    }

    val optional = declaration?.optional == true
    val sort = declaration?.let { typeToSort(it.type) } ?: unresolvedSort
    val expectedKind = when (sort) {
        boolSort -> value.type.boolTypeExpr
        fp64Sort -> value.type.fpTypeExpr
        addressSort -> value.type.refTypeExpr
        else -> trueExpr
    }
    val isUndefined = mkHeapRefEq(value.refValue, mkUndefinedValue())
    val undefinedKind = mkAnd(value.type.refTypeExpr, isUndefined)
    val allowedKind = if (optional) mkOr(expectedKind, undefinedKind) else expectedKind
    val exactlyOneKind = value.type.mkExactlyOneTypeConstraint(this)
    val initialKindConstraint = mkImplies(activeInitial, mkAnd(allowedKind, exactlyOneKind))
    // Use the selectors stored on the input reference, so differently typed aliases agree.
    scope.assert(initialKindConstraint) ?: return null

    // Optional references may be undefined even when the field exists.
    val activeReference = if (optional) mkAnd(activeInitial, mkNot(isUndefined)) else activeInitial
    val referenceConstraint = initialReferenceConstraint(scope, value.refValue, declaration?.type, hierarchy)
    val activeReferenceConstraint = mkImplies(activeReference, referenceConstraint)
    scope.assert(activeReferenceConstraint) ?: return null

    if (declaration?.type !is EtsStringType && declaration?.type !is EtsStringLiteralType) return value

    val stringRef = materializeTypedStringField(
        scope = scope,
        value = value.refValue,
        maxStringLength = scope.calcOnState { maxStringLength },
        literal = (declaration.type as? EtsStringLiteralType)?.value,
        activeGuard = activeReference,
    ) ?: return null
    val initialRef = if (optional) mkIte(isUndefined, mkUndefinedValue(), stringRef) else stringRef
    return value.copy(refValue = initialRef)
}

private fun TsContext.initialReferenceConstraint(
    scope: TsStepScope,
    ref: UHeapRef,
    type: EtsType?,
    hierarchy: EtsHierarchy,
): UBoolExpr = when (type) {
    is EtsClassType -> scope.calcOnState {
        val auxiliary = type.toAuxiliaryType(hierarchy)
        val classConstraint = auxiliary?.let { memory.types.evalIsSubtype(ref, it) } ?: trueExpr
        mkAnd(mkNotNullOrUndefined(ref), classConstraint)
    }
    is EtsNullType -> mkHeapRefEq(ref, mkTsNullValue())
    is EtsUndefinedType -> mkHeapRefEq(ref, mkUndefinedValue())
    else -> trueExpr
}

/** Reading a field always produces a value; path validation belongs to [resolveField]. */
private fun TsContext.readField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
    sort: USort,
): UExpr<*> {
    if (sort !is TsUnresolvedSort) {
        val lValue = mkFieldLValue(sort, instance, field.name)
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
        if (ref.hasFakeValueBranch()) {
            boxRuntimeValue(scope = scope, value = ref)
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
    activeGuard: UBoolExpr = trueExpr,
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
        val initialValueIsInactive = mkNot(activeGuard)
        scope.fork(initialValueIsInactive, blockOnFalseState = {
            terminateAsUnsupported(reason = "Literal string field exceeds configured string length $maxStringLength")
        }) ?: return null
        return value
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
    val activeStringConstraints = mkImplies(activeGuard, constraints)
    scope.assert(activeStringConstraints) ?: return null

    scope.doWithState { boundedStringBackingRefs += stringRef to maxStringLength }

    return stringRef
}

internal fun TsExprResolver.handleStaticFieldRef(
    value: EtsStaticFieldRef,
): UExpr<*>? = with(ctx) {
    if (value.field.name == "prototype") {
        val constructor = classConstructorRef(value.field.enclosingClass)
        if (!ensureSupportedArrayPrototypeAccess(constructor, value.field.name)) return null
        ensureSupportedDatePrototypeAccess(constructor, value.field.name)
    }

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

private val NATIVE_TYPED_ARRAY_NAMES = setOf(
    "Int8Array", "Uint8Array", "Uint8ClampedArray", "Int16Array", "Uint16Array",
    "Int32Array", "Uint32Array", "Float32Array", "Float64Array", "BigInt64Array", "BigUint64Array",
)
