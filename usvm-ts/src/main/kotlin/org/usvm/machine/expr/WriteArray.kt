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
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.typedArrayOverrideLValue
import org.usvm.util.typedArrayBoxedLValue
import org.usvm.util.boxArrayValue

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
        throw UnsupportedOperationException("Writes to string indexes are not modeled")
    }

    val propertyName = concreteStringValue(resolvedIndex)
    if (propertyName != null && storageType !is EtsArrayType) {
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
        logger.warn { "Unsupported named array property write for key: $resolvedIndex" }
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            detail = "property key is not a supported numeric array index: $resolvedIndex",
        )
    }
    scope.assert(indexIsSupported) ?: return null

    val bvIndex = mkFpToUint32AfterValidation(index.value, indexIsSupported).asExpr(sizeSort)

    return assignToArrayIndex(
        scope = scope,
        array = array,
        index = bvIndex,
        expr = expr,
        arrayType = storageType,
        onUnsupportedElementKind = {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_INPUT_MIXED_KIND_WRITE,
                detail = "input array write has a runtime kind outside its initialized typed backing",
            )
        },
        onUnsupportedGrowth = {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
                detail = "array index write would grow beyond the current length",
            )
        },
    )
}

fun TsContext.assignToArrayIndex(
    scope: TsStepScope,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    expr: UExpr<*>,
    arrayType: EtsArrayType,
    onUnsupportedGrowth: (() -> Unit)? = null,
    onUnsupportedElementKind: (() -> Unit)? = null,
): Unit? {
    checkNotFake(array)

    // Read the array length.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    // Note: out-of-bound write is not an error in JS, since it can grow the array.
    //  However, we decided to forbid this behavior in our model for simplicity.
    //  Instead, we only allow writing to existing indices.

    val indexIsNonNegative = mkBvSignedGreaterOrEqualExpr(index, mkBv(0))
    val indexIsBelowLength = mkBvSignedLessExpr(index, length)
    val indexIsInRange = mkAnd(indexIsNonNegative, indexIsBelowLength)
    if (scope.checkSat(mkNot(indexIsInRange)) != null) {
        logger.warn { "Unsupported array growth through index write: index=$index, length=$length" }
        onUnsupportedGrowth?.invoke()
    }
    scope.assert(indexIsInRange) ?: return null

    val elementSort = typeToSort(arrayType.elementType)

    // If the element sort is known, write directly.
    if (elementSort !is TsUnresolvedSort) {
        val containsFakeValue = expr.sort == addressSort && expr.asExpr(addressSort).hasFakeValueBranch()
        val directPayload = expr.sort == elementSort && !containsFakeValue
        if (directPayload) {
            val lValue = mkArrayIndexLValue(elementSort, array, index, arrayType)
            return scope.doWithState {
                memory.write(lValue, expr.asExpr(elementSort), guard = trueExpr)
                if (isAllocatedConcreteHeapRef(array)) {
                    memory.write(typedArrayOverrideLValue(array, index), falseExpr, guard = trueExpr)
                }
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
        val allocated = isAllocatedConcreteHeapRef(array)
        if (!allocated) {
            val mismatchedKindIsFeasible = scope.checkSat(mkNot(compatibleKind)) != null
            if (mismatchedKindIsFeasible) onUnsupportedElementKind?.invoke()
            scope.assert(compatibleKind) ?: return null
        }

        val lValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
            index = index.asExpr(sizeSort),
            type = arrayType,
        )
        return scope.doWithState {
            memory.write(lValue, payload.asExpr(elementSort), guard = compatibleKind)
            if (allocated) {
                memory.write(typedArrayBoxedLValue(array, index), fake, guard = trueExpr)
                memory.write(typedArrayOverrideLValue(array, index), mkNot(compatibleKind), guard = trueExpr)
            }
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
