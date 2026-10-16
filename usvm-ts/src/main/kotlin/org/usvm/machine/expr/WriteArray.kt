package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.boxArrayValue
import org.usvm.util.componentType
import org.usvm.util.initializeTypedArrayOverrides
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.typedArrayBoxedLValue
import org.usvm.util.typedArrayOverrideLValue

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleAssignToArrayIndex(
    lhv: EtsArrayAccess,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    // Resolve the array.
    val resolvedArray = resolve(lhv.array) ?: return null
    check(resolvedArray.sort == addressSort) {
        "Expected address sort for array, got: ${resolvedArray.sort}"
    }
    val rawArray = if (resolvedArray.isFakeObject()) {
        scope.assert(resolvedArray.getFakeType(scope).refTypeExpr) ?: return null
        resolvedArray.extractRef(scope)
    } else {
        resolvedArray.asExpr(addressSort)
    }
    checkUndefinedOrNullPropertyRead(scope, rawArray, propertyName = "[]") ?: return null
    val array = resolveHeapRef(scope, rawArray) ?: return null

    handleAssignToArrayIndex(lhv = lhv, expr = expr, array = array)
}

internal fun TsExprResolver.handleAssignToArrayIndex(
    lhv: EtsArrayAccess,
    expr: UExpr<*>,
    array: UHeapRef,
): Unit? = with(ctx) {
    // Check for undefined or null array access.
    checkUndefinedOrNullPropertyRead(scope, array, propertyName = "[]") ?: return null

    // Resolve the index.
    val resolvedIndex = resolve(lhv.index) ?: return null
    val storageType = scope.calcOnState { arrayStorageType(array, lhv.array.type) }
    if (storageType == EtsStringType) {
        return handleStringPropertyWrite()
    }

    val propertyName = concreteStringValue(resolvedIndex)
    if (propertyName != null && storageType !is EtsArrayType) {
        if (!ensureSupportedArrayPrototypeAccess(array, propertyName)) return null

        val field = EtsFieldSignature(
            name = propertyName,
            enclosingClass = EtsClassSignature.UNKNOWN,
            type = EtsUnknownType,
        )
        return assignToInstanceField(scope, lhv.array, array, field, expr, hierarchy)
    }

    if (storageType !is EtsArrayType) {
        stopRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_STORAGE_TYPE,
            detail = "Indexed access requires supported storage: static=${lhv.array.type}, storage=$storageType",
        )
        return null
    }

    val index = extractNumericArrayIndex(scope, resolvedIndex)

    val indexIsSupported = mkAnd(
        index.isNumeric,
        mkValidArrayIndexProperty(
            value = index.value,
        ),
    )
    val unsupportedIndexIsFeasible = scope.checkSat(mkNot(indexIsSupported)) != null
    if (unsupportedIndexIsFeasible) {
        logger.warn { "Unsupported named array property write: key is not a supported numeric array index" }
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            detail = "property key is not a supported numeric array index",
        )
    }
    scope.fork(indexIsSupported, blockOnFalseState = {
        terminateAsUnsupported(
            reason = "${TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE}: " +
                "Property key is not a supported numeric array index",
        )
    }) ?: return null

    val bvIndex = mkFpToUint32AfterValidation(index.value, indexIsSupported).asExpr(sizeSort)
    extendArrayForIndexAppend(array = array, index = bvIndex, arrayType = storageType) ?: return null

    return assignToArrayIndex(
        scope = scope,
        array = array,
        index = bvIndex,
        expr = expr,
        arrayType = storageType,
        onUnsupportedGrowth = {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
                detail = "array index write would grow beyond the current length",
            )
        },
    )
}

private fun TsExprResolver.extendArrayForIndexAppend(
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    arrayType: EtsArrayType,
): Unit? = with(ctx) {
    val lengthLValue = mkArrayLengthLValue(array, arrayType)
    val length = scope.calcOnState { memory.read(lengthLValue) }
    val isAppend = mkEq(index, length)
    val belowCapacity = mkBvSignedLessExpr(length, mkBv(options.maxArraySize))
    val appendExceedsCapacity = mkAnd(isAppend, mkNot(belowCapacity))
    if (scope.checkSat(appendExceedsCapacity) != null) {
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
            detail = "array index append exceeds configured length bound ${options.maxArraySize}",
        )
    }
    scope.assert(mkNot(appendExceedsCapacity)) ?: return null

    // Dense appends have no uninitialized gap. Sparse growth remains unsupported below.
    scope.doWithState {
        memory.write(lengthLValue, mkBvAddExpr(length, mkBv(1)), guard = isAppend)
    }
}

fun TsContext.assignToArrayIndex(
    scope: TsStepScope,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    expr: UExpr<*>,
    arrayType: EtsArrayType,
    onUnsupportedGrowth: (() -> Unit)? = null,
): Unit? {
    checkNotFake(array)

    // Read the array length.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    // Callers extend the length for supported dense appends before writing the element.
    // Skipping indices would create holes, which this storage model does not represent.

    val indexIsNonNegative = mkBvSignedGreaterOrEqualExpr(index, mkBv(0))
    val indexIsBelowLength = mkBvSignedLessExpr(index, length)
    val indexIsInRange = mkAnd(indexIsNonNegative, indexIsBelowLength)
    if (scope.checkSat(mkNot(indexIsInRange)) != null) {
        // Printing symbolic indices or lengths can recursively expand collection updates into enormous strings.
        logger.warn { "Unsupported array growth through index write: sparse growth is not modeled" }
        onUnsupportedGrowth?.invoke()
    }
    scope.assert(indexIsInRange) ?: return null

    scope.doWithState {
        val writtenIndices = initializedArrayIndices[array].orEmpty()
        initializedArrayIndices += array to (writtenIndices + (index to trueExpr))
    }

    val elementSort = typeToSort(arrayType.componentType)

    // If the element sort is known, write directly.
    if (elementSort !is TsUnresolvedSort) {
        val containsFakeValue = expr.sort == addressSort && expr.asExpr(addressSort).hasFakeValueBranch()
        val directPayload = expr.sort == elementSort && !containsFakeValue
        if (directPayload) {
            val lValue = mkArrayIndexLValue(elementSort, array, index, arrayType)
            return scope.doWithState {
                memory.write(lValue, expr.asExpr(elementSort), guard = trueExpr)
                initializeTypedArrayOverrides(array)
                memory.write(typedArrayOverrideLValue(array, index), falseExpr, guard = trueExpr)
            }
        }

        val fake = boxArrayValue(scope, expr)
        val kind = fake.getFakeType(scope)
        val compatibleKind = when (elementSort) {
            boolSort -> kind.boolTypeExpr
            fp64Sort -> kind.fpTypeExpr
            addressSort -> kind.refTypeExpr
            else -> error("Unsupported typed array element sort: $elementSort")
        }
        val payload = when (elementSort) {
            boolSort -> fake.extractBool(scope)
            fp64Sort -> fake.extractFp(scope)
            addressSort -> fake.extractRef(scope)
            else -> error("Unsupported typed array element sort: $elementSort")
        }
        val lValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
            index = index.asExpr(sizeSort),
            type = arrayType,
        )
        return scope.doWithState {
            memory.write(lValue, payload.asExpr(elementSort), guard = compatibleKind)
            initializeTypedArrayOverrides(array)
            memory.write(typedArrayBoxedLValue(array, index), fake, guard = trueExpr)
            memory.write(typedArrayOverrideLValue(array, index), mkNot(compatibleKind), guard = trueExpr)
        }
    }

    // If the element sort is unknown, we need to employ a fake object.
    val lValue = mkArrayIndexLValue(
        sort = addressSort,
        ref = array,
        index = index.asExpr(sizeSort),
        type = arrayType,
    )
    val fakeExpr = boxArrayValue(scope, expr)
    return scope.doWithState {
        memory.write(lValue, fakeExpr, guard = trueExpr)
    }
}
