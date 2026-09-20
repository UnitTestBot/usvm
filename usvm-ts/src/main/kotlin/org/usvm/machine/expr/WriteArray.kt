package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

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
    val array = resolvedArray.asExpr(addressSort)

    handleAssignToArrayIndex(lhv, expr, array)
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
    val index = extractNumericArrayIndex(scope, resolvedIndex)

    val indexIsSupported = mkAnd(
        index.isNumeric,
        mkValidArrayIndexProperty(
            value = index.value,
            maximumSupportedIndex = options.maxArraySize,
        ),
    )
    if (scope.checkSat(mkNot(indexIsSupported)) != null) {
        logger.warn { "Unsupported named array property write for key: $resolvedIndex" }
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            detail = "property key is not a supported numeric array index: $resolvedIndex",
        )
    }
    scope.assert(indexIsSupported) ?: return null

    val bvIndex = mkFpToUint32AfterValidation(index.value, indexIsSupported).asExpr(sizeSort)

    val arrayType = scope.calcOnState { arrayStorageType(array, lhv.array.type) }
    check(arrayType is EtsArrayType) {
        "Expected EtsArrayType, got: ${lhv.array.type}"
    }

    return assignToArrayIndex(
        scope = scope,
        array = array,
        index = bvIndex,
        expr = expr,
        arrayType = arrayType,
        onUnsupportedGrowth = {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
                detail = "array index write would grow beyond the current length",
            )
        },
        onUnsupportedElementKind = {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_ELEMENT_KIND_WRITE,
                detail = "array storage cannot represent the assigned runtime value kind",
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
        val (payload, kindGuard) = if (expr.isFakeObject()) {
            val type = expr.getFakeType(scope)
            when (elementSort) {
                boolSort -> expr.extractBool(scope) to type.boolTypeExpr
                fp64Sort -> expr.extractFp(scope) to type.fpTypeExpr
                addressSort -> expr.extractRef(scope) to type.refTypeExpr
                else -> error("Unsupported array element sort: $elementSort")
            }
        } else {
            expr to mkBool(expr.sort == elementSort)
        }
        if (scope.checkSat(mkNot(kindGuard)) != null) {
            onUnsupportedElementKind?.invoke()
        }
        scope.assert(kindGuard) ?: return null

        val lValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
            index = index.asExpr(sizeSort),
            type = arrayType,
        )
        return scope.doWithState {
            memory.write(lValue, payload.asExpr(elementSort), guard = trueExpr)
        }
    }

    // If the element sort is unknown, we need to employ a fake object.
    val lValue = mkArrayIndexLValue(
        sort = addressSort,
        ref = array,
        index = index.asExpr(sizeSort),
        type = arrayType,
    )
    val fakeExpr = expr.toFakeObject(scope)
    return scope.doWithState {
        memory.write(lValue, fakeExpr, guard = trueExpr)
    }
}
