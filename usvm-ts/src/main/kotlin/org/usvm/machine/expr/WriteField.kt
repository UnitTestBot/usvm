package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStaticFieldRef
import org.usvm.UExpr
import org.usvm.UHeapRef
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
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.resolveEtsField

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleAssignToInstanceField(
    lhv: EtsInstanceFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    val instanceLocal = lhv.instance
    val field = lhv.field

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

    // Check for undefined or null field access.
    checkUndefinedOrNullPropertyRead(scope, instance, field.name) ?: return null

    val arrayType = scope.calcOnState { arrayStorageType(instance, instanceLocal.type) } as? EtsArrayType
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
    // Unwrap to get non-fake reference.
    val unwrappedInstance = instance.unwrapRef(scope)

    val etsField = resolveEtsField(instanceLocal, field, hierarchy)
    // If we access some field, we expect that the object must have this field.
    // It is not always true for TS, but we decided to process it so.
    if (!field.isModelStorageField()) {
        val supertype = EtsAuxiliaryType(properties = setOf(field.name))
        val propertyExists = scope.calcOnState { memory.types.evalIsSubtype(unwrappedInstance, supertype) }
        // The assertion is required to update models before the write.
        scope.assert(propertyExists) ?: return null
    }

    // Determine the field sort.
    val sort = when (etsField) {
        is TsResolutionResult.Empty -> unresolvedSort
        is TsResolutionResult.Unique -> typeToSort(etsField.property.type)
        is TsResolutionResult.Ambiguous -> unresolvedSort
    }

    // If the field type is unknown, we create a fake object for the expr and assign it.
    // Otherwise, assign expr directly.
    return scope.doWithState {
        if (sort is TsUnresolvedSort) {
            val fakeObject = expr.toFakeObject(scope)
            val lValue = mkFieldLValue(addressSort, unwrappedInstance, field)
            lValuesToAllocatedFakeObjects += lValue to fakeObject
            memory.write(lValue, fakeObject, guard = trueExpr)
        } else {
            val lValue = mkFieldLValue(sort, unwrappedInstance, field)
            if (lValue.sort != expr.sort) {
                if (expr.isFakeObject()) {
                    val lhvType = instanceLocal.type
                    val value = when (lhvType) {
                        is EtsBooleanType -> {
                            pathConstraints += expr.getFakeType(scope).boolTypeExpr
                            expr.extractBool(scope)
                        }

                        is EtsNumberType -> {
                            pathConstraints += expr.getFakeType(scope).fpTypeExpr
                            expr.extractFp(scope)
                        }

                        else -> {
                            pathConstraints += expr.getFakeType(scope).refTypeExpr
                            expr.extractRef(scope)
                        }
                    }
                    memory.write(lValue, value.asExpr(lValue.sort), guard = trueExpr)
                } else {
                    TODO("Support enums fields")
                }
            } else {
                memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
            }
        }
    }
}

private fun EtsFieldSignature.isModelStorageField(): Boolean = when (enclosingClass.name) {
    "DateValue" -> name == "timestamp"
    "ErrorValue" -> {
        enclosingClass.file.fileName == "ErrorModels.ts" && (name == "name" || name == "message")
    }
    else -> false
}

internal fun TsExprResolver.handleAssignToStaticField(
    lhv: EtsStaticFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    assignToStaticField(scope, lhv.field, expr)
}

fun TsContext.assignToStaticField(
    scope: TsStepScope,
    field: EtsFieldSignature,
    expr: UExpr<*>,
): Unit? {
    val clazz = scene.projectAndSdkClasses.singleOrNull {
        it.signature == field.enclosingClass
    } ?: return null

    // Static initialization precedes the first read or write. In particular,
    // a module initializer must not overwrite a value written by the caller.
    ensureStaticsInitialized(scope, clazz) ?: return null

    val instance = scope.calcOnState { getStaticInstance(clazz) }

    val sort = run {
        val fields = clazz.fields.filter { it.name == field.name }
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
        scope.doWithState {
            memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
        }
    }
}
