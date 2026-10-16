package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureStaticsInitialized
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.extractValue
import org.usvm.sizeSort
import org.usvm.util.EtsHierarchy
import org.usvm.util.TsResolutionResult
import org.usvm.util.arrayStorageType
import org.usvm.util.hasStringName
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.requireStringKey
import org.usvm.util.resolveEtsField
import org.usvm.util.truncateKnownArrayIndices
import org.usvm.util.boxArrayValue as boxRuntimeValue

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleAssignToClosureField(
    lhv: EtsClosureFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    val instance = resolve(lhv.base) ?: return null
    assignToClosureField(scope, instance.asExpr(addressSort), lhv.fieldName, lhv.type, expr)
}

internal fun TsContext.assignToClosureField(
    scope: TsStepScope,
    instance: UHeapRef,
    fieldName: String,
    fieldType: EtsType,
    expr: UExpr<*>,
): Unit? {
    check(isAllocatedConcreteHeapRef(instance)) {
        "Closure object should be a concrete heap reference, but got $instance"
    }

    val declaredSort = typeToSort(fieldType)
    val sort = if (declaredSort == unresolvedSort) addressSort else declaredSort
    val lValue = mkFieldLValue(sort, instance, fieldName)
    val containsFakeValue = expr.sort == addressSort && expr.asExpr(addressSort).hasFakeValueBranch()
    val fakeObject = if (declaredSort == unresolvedSort || containsFakeValue) {
        boxRuntimeValue(scope = scope, value = expr)
    } else {
        null
    }
    val value = fakeObject ?: expr

    if (declaredSort != unresolvedSort) {
        ensureCompatibleFieldWriteKind(scope, fieldName, sort, value) ?: return null
    }

    val payload = if (declaredSort != unresolvedSort && value.isFakeObject()) {
        when (sort) {
            boolSort -> value.extractBool(scope)
            fp64Sort -> value.extractFp(scope)
            addressSort -> value.extractRef(scope)
            else -> error("Unsupported closure field sort: $sort")
        }
    } else {
        value
    }

    return scope.doWithState {
        if (declaredSort == unresolvedSort) lValuesToAllocatedFakeObjects += lValue to requireNotNull(fakeObject)
        memory.write(lValue, payload.asExpr(sort), guard = trueExpr)
    }
}

internal fun TsExprResolver.handleAssignToInstanceField(
    lhv: EtsInstanceFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    val instanceLocal = lhv.instance
    val field = lhv.field

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

    // Check for undefined or null field access.
    checkUndefinedOrNullPropertyRead(scope, rawInstance, field.name) ?: return null
    val instance = resolveHeapRef(scope, rawInstance) ?: return null
    if (!ensureSupportedArrayPrototypeAccess(instance, field.name)) return null
    ensureSupportedDatePrototypeAccess(instance, field.name)

    val storageType = scope.calcOnState { arrayStorageType(instance, instanceLocal.type) }
    if (storageType == EtsStringType) {
        return handleStringPropertyWrite()
    }

    val arrayType = storageType as? EtsArrayType
    if (field.name == "length" && arrayType != null) {
        return assignToArrayLength(
            scope = scope,
            array = instance,
            arrayType = arrayType,
            value = expr,
            maxArraySize = options.maxArraySize,
            onFeatureLimitation = ::reportRuntimeFeatureLimitation,
        )
    }

    // Assign to the field.
    assignToInstanceField(scope, instanceLocal, instance, field, expr, hierarchy)
}

private fun TsContext.assignToArrayLength(
    scope: TsStepScope,
    array: UHeapRef,
    arrayType: EtsArrayType,
    value: UExpr<*>,
    maxArraySize: Int,
    onFeatureLimitation: (TsRuntimeFeatureLimitationReason, String) -> Unit,
): Unit? = with(this) {
    val (fpLength, numericTypeGuard) = scope.calcOnState {
        with(ctx) {
            extractValue(value, fp64Sort, ::getIntermediateFpLValue)
        }
    }
    // Assertions update both path constraints and cached models through the state forker.
    val numericTypeIsPossible = scope.assert(numericTypeGuard)
    if (fpLength == null || numericTypeIsPossible == null) {
        logger.warn {
            "Unsupported array length assignment: runtime value is not numeric (storage sort: ${value.sort})"
        }
        return null
    }

    val validJsLength = mkValidArrayLength(fpLength)
    scope.fork(
        validJsLength,
        blockOnFalseState = { throwException("RangeError: Invalid array length: $fpLength") },
    ) ?: return null

    val maximumSupportedLength = mkFp64(maxArraySize.toDouble())
    val withinModelCapacity = mkFpLessOrEqualExpr(fpLength, maximumSupportedLength)
    if (scope.checkSat(mkNot(withinModelCapacity)) != null) {
        onFeatureLimitation(
            TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
            "assigned array length exceeds model capacity: $fpLength",
        )
    }
    scope.assert(withinModelCapacity) ?: run {
        logger.warn { "Unsupported array length assignment beyond model capacity: $fpLength" }
        return null
    }

    val length = mkFpToUint32AfterValidation(fpLength, validJsLength).asExpr(sizeSort)
    val lengthLValue = mkArrayLengthLValue(array, arrayType)
    val currentLength = scope.calcOnState {
        memory.read(lengthLValue)
    }
    val lengthIsNotGrowing = mkBvSignedLessOrEqualExpr(length, currentLength)
    if (scope.checkSat(mkNot(lengthIsNotGrowing)) != null) {
        onFeatureLimitation(
            TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_GROWTH,
            "assigned array length would grow beyond the current length",
        )
    }
    scope.assert(lengthIsNotGrowing) ?: run {
        logger.warn {
            "Unsupported array length growth: expected length at most the current length, " +
                "but the constraint is UNSAT: $lengthIsNotGrowing"
        }
        return null
    }

    return scope.doWithState {
        memory.write(lengthLValue, length, guard = trueExpr)
        truncateKnownArrayIndices(array, length)
    }
}

fun TsContext.assignToInstanceField(
    scope: TsStepScope,
    instanceLocal: EtsLocal,
    instance: UHeapRef,
    field: EtsFieldSignature,
    expr: UExpr<*>,
    hierarchy: EtsHierarchy,
): Unit? {
    field.requireStringKey()

    // Unwrap to get non-fake reference.
    val unwrappedInstance = resolveHeapRef(scope, instance.unwrapRef(scope)) ?: return null

    when (val intrinsic = tryWriteIntrinsicField(scope, unwrappedInstance, field, expr)) {
        is TsIntrinsicFieldResult.Handled -> return intrinsic.value
        TsIntrinsicFieldResult.Stopped -> return null
        TsIntrinsicFieldResult.NotApplicable -> {}
    }

    if (!isAllocatedConcreteHeapRef(unwrappedInstance) && instanceLocal.type !is EtsArrayType) {
        trackInputProperty(scope, unwrappedInstance, instanceLocal, field.name, hierarchy) ?: return null
        writeInputPropertyValue(scope, unwrappedInstance, field.name, expr)
        return Unit
    }

    val wroteModeledErrorField = writeModeledErrorField(
        scope = scope,
        instance = unwrappedInstance,
        field = field,
        value = expr,
    )
    if (wroteModeledErrorField) return Unit

    val objectLiteralClass = objectLiteralClass(scope, unwrappedInstance, hierarchy)
    if (objectLiteralClass == null) {
        ensureSupportedDeclaredMemberAccess(scope, unwrappedInstance, instanceLocal, field.name, hierarchy)
    }

    // An object literal can acquire a new own property after creation. Requiring its
    // allocation type to declare the field would reject that valid JavaScript write.
    if (objectLiteralClass == null) {
        val fieldExists = scope.calcOnState {
            val supertype = EtsAuxiliaryType(properties = setOf(field.name))
            memory.types.evalIsSubtype(unwrappedInstance, supertype)
        }
        scope.assert(fieldExists) ?: return null
    }

    val containsBoxedValue = expr.sort == addressSort && expr.asExpr(addressSort).hasFakeValueBranch()

    // Determine the field sort.
    val sort = when {
        objectLiteralClass != null -> {
            // Literal declarations describe inferred source types, not runtime restrictions.
            // Optional values can be undefined even when the frontend inferred a primitive field.
            if (containsBoxedValue) unresolvedSort else expr.sort
        }

        else -> when (val etsField = resolveEtsField(instanceLocal, field, hierarchy)) {
            is TsResolutionResult.Empty -> unresolvedSort
            is TsResolutionResult.Unique -> typeToSort(etsField.property.type)
            is TsResolutionResult.Ambiguous -> resolveAmbiguousFieldSort(
                scope = scope,
                instance = unwrappedInstance,
                fields = etsField.properties,
                hierarchy = hierarchy,
            )
        }
    }

    if (sort !is TsUnresolvedSort) {
        ensureCompatibleFieldWriteKind(scope, field.name, sort, expr) ?: return null
    }

    // If the field type is unknown, we create a fake object for the expr and assign it.
    // Otherwise, assign expr directly.
    return scope.doWithState {
        if (sort is TsUnresolvedSort) {
            val fakeObject = boxRuntimeValue(scope = scope, value = expr)
            val lValue = mkFieldLValue(addressSort, unwrappedInstance, field)
            lValuesToAllocatedFakeObjects += lValue to fakeObject
            memory.write(lValue, fakeObject, guard = trueExpr)
        } else {
            val lValue = mkFieldLValue(sort, unwrappedInstance, field)
            if (lValue.sort != expr.sort) {
                check(expr.isFakeObject())
                val value = when (sort) {
                    boolSort -> expr.extractBool(scope)
                    fp64Sort -> expr.extractFp(scope)
                    addressSort -> expr.extractRef(scope)
                    else -> error("Unsupported field sort: $sort")
                }
                memory.write(lValue, value.asExpr(lValue.sort), guard = trueExpr)
            } else {
                memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
            }
        }

        memory.write(deletedFieldLValue(unwrappedInstance, field), falseExpr, guard = trueExpr)
        if (isAllocatedConcreteHeapRef(unwrappedInstance)) {
            writtenConcreteFields = writtenConcreteFields + (unwrappedInstance to field.name)
            if (objectLiteralClass != null) {
                saveObjectLiteralFieldSort(unwrappedInstance, field.name, sort)
            }
        }
    }
}

private fun TsContext.ensureCompatibleFieldWriteKind(
    scope: TsStepScope,
    fieldName: String,
    sort: USort,
    expr: UExpr<*>,
): Unit? {
    if (expr.isFakeObject()) {
        val fakeType = scope.calcOnState { expr.getFakeType(scope) }
        val compatibleType = when (sort) {
            boolSort -> fakeType.boolTypeExpr
            fp64Sort -> fakeType.fpTypeExpr
            addressSort -> fakeType.refTypeExpr
            else -> error("Unsupported field sort: $sort")
        }

        scope.fork(
            compatibleType,
            blockOnFalseState = {
                terminateAsUnsupported(reason = "Assignment changes runtime sort of field '$fieldName'")
            },
        ) ?: return null
        return Unit
    }

    if (expr.sort != sort) {
        throw UnsupportedOperationException("Assignment changes runtime sort of field '$fieldName'")
    }

    return Unit
}

internal fun TsExprResolver.handleAssignToStaticField(
    lhv: EtsStaticFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    if (lhv.field.name == "prototype") {
        val constructor = classConstructorRef(lhv.field.enclosingClass)
        if (!ensureSupportedArrayPrototypeAccess(constructor, lhv.field.name)) return null
    }

    assignToStaticField(scope, lhv.field, expr)
}

fun TsContext.assignToStaticField(
    scope: TsStepScope,
    field: EtsFieldSignature,
    expr: UExpr<*>,
): Unit? {
    field.requireStringKey()

    val clazz = scene.projectAndSdkClasses.singleOrNull {
        it.signature == field.enclosingClass
    } ?: return null

    // Static initialization precedes the first read or write. In particular,
    // a module initializer must not overwrite a value written by the caller.
    ensureStaticsInitialized(scope, clazz) ?: return null

    val instance = scope.calcOnState { getStaticInstance(clazz) }

    val sort = run {
        val fields = clazz.fields.filter { it.hasStringName(field.name) }
        if (fields.size == 1) {
            val field = fields.single()
            val sort = typeToSort(field.type)
            return@run sort
        }
        unresolvedSort
    }
    return if (sort == unresolvedSort) {
        val lValue = mkFieldLValue(addressSort, instance, field.name)
        val fakeObject = expr.toFakeObject(scope)
        scope.doWithState {
            lValuesToAllocatedFakeObjects += lValue to fakeObject
            memory.write(lValue, fakeObject, guard = trueExpr)
        }
    } else {
        val lValue = mkFieldLValue(sort, instance, field.name)
        // A conditional reference can contain a fake value on only one branch.
        val mayContainFakeValue = expr.sort == addressSort && expr.asExpr(addressSort).hasFakeValueBranch()
        val value = if (mayContainFakeValue) {
            expr.toFakeObject(scope)
        } else {
            expr
        }
        ensureCompatibleFieldWriteKind(scope, field.name, sort, value) ?: return null

        val payload = if (value.isFakeObject()) {
            when (sort) {
                boolSort -> value.extractBool(scope)
                fp64Sort -> value.extractFp(scope)
                addressSort -> value.extractRef(scope)
                else -> error("Unsupported field sort: $sort")
            }
        } else {
            value
        }
        scope.doWithState {
            memory.write(lValue, payload.asExpr(lValue.sort), guard = trueExpr)
        }
    }
}
