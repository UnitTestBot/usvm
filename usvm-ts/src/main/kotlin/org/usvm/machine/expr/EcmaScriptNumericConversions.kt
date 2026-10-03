package org.usvm.machine.expr

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.extractValue

private const val UINT32_MODULUS = 4_294_967_296.0
private const val UINT32_MAX_VALUE = 4_294_967_295.0

internal data class TsNumericArrayIndex(
    val value: UExpr<KFp64Sort>,
    val isNumeric: UBoolExpr,
    val hasUnsupportedReadKey: UBoolExpr,
)

/** Classifies an index that may be wrapped with another runtime type. */
internal fun TsContext.extractNumericArrayIndex(
    scope: TsStepScope,
    index: UExpr<out USort>,
): TsNumericArrayIndex {
    val runtimeValues = scope.calcOnState {
        with(ctx) {
            val (_, boolGuard) = extractValue(index, boolSort, ::getIntermediateBoolLValue)
            val (fpValue, fpGuard) = extractValue(index, fp64Sort, ::getIntermediateFpLValue)
            val (reference, refGuard) = extractValue(index, addressSort, ::getIntermediateRefLValue)

            IndexRuntimeValues(
                boolTypeGuard = boolGuard,
                numericValue = fpValue,
                numericTypeGuard = fpGuard,
                refValue = reference,
                refTypeGuard = refGuard,
            )
        }
    }
    val refIsKnownMissingKey = if (runtimeValues.refValue == null) {
        falseExpr
    } else {
        val refIsNull = mkHeapRefEq(runtimeValues.refValue, mkTsNullValue())
        val refIsUndefined = mkHeapRefEq(runtimeValues.refValue, mkUndefinedValue())
        val refIsNullish = mkOr(refIsNull, refIsUndefined)

        mkAnd(
            runtimeValues.refTypeGuard,
            refIsNullish,
        )
    }
    val hasSupportedRuntimeType = mkOr(
        runtimeValues.boolTypeGuard,
        runtimeValues.numericTypeGuard,
        refIsKnownMissingKey,
    )

    return TsNumericArrayIndex(
        value = runtimeValues.numericValue ?: mkFp64(0.0),
        isNumeric = runtimeValues.numericTypeGuard,
        hasUnsupportedReadKey = mkNot(hasSupportedRuntimeType),
    )
}

private data class IndexRuntimeValues(
    val boolTypeGuard: UBoolExpr,
    val numericValue: UExpr<KFp64Sort>?,
    val numericTypeGuard: UBoolExpr,
    val refValue: UHeapRef?,
    val refTypeGuard: UBoolExpr,
)

/** ECMAScript ToUint32, represented as the common 32-bit bit pattern used by ToInt32. */
internal fun TsContext.mkEcmaScriptToUint32(value: UExpr<KFp64Sort>): UExpr<KBvSort> = with(this) {
    val zero = mkFp64(0.0)
    val isFinite = mkAnd(
        mkFpIsNaNExpr(value).not(),
        mkFpIsInfiniteExpr(value).not(),
    )
    val finiteValue = mkIte(isFinite, value, zero)
    val towardZero = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero)
    val integer = mkFpRoundToIntegralExpr(
        roundingMode = towardZero,
        value = finiteValue,
    )
    val modulus = mkFp64(UINT32_MODULUS)
    val remainder = mkFpRemExpr(integer, modulus)
    val remainderIsNegative = mkFpLessExpr(remainder, zero)
    val positiveRemainder = mkFpAddExpr(fpRoundingModeSortDefaultValue(), remainder, modulus)
    val normalizedRemainder = mkIte(
        condition = remainderIsNegative,
        trueBranch = positiveRemainder,
        falseBranch = remainder,
    )

    mkFpToBvExpr(
        roundingMode = towardZero,
        value = normalizedRemainder,
        bvSize = bv32Sort.sizeBits.toInt(),
        isSigned = false,
    )
}

internal fun TsContext.mkValidArrayIndexProperty(
    value: UExpr<KFp64Sort>,
    maximumSupportedIndex: Int,
): UBoolExpr = mkFiniteIntegerInRange(
    value = value,
    minimum = 0.0,
    maximum = maximumSupportedIndex.toDouble(),
)

internal fun TsContext.mkValidArrayLength(value: UExpr<KFp64Sort>): UBoolExpr = mkFiniteIntegerInRange(
    value = value,
    minimum = 0.0,
    maximum = UINT32_MAX_VALUE,
)

internal fun TsContext.mkFpToUint32AfterValidation(
    value: UExpr<KFp64Sort>,
    validity: UBoolExpr,
): UExpr<KBvSort> = with(this) {
    val safeValue = mkIte(
        condition = validity,
        trueBranch = value,
        falseBranch = mkFp64(0.0),
    )
    mkFpToBvExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
        value = safeValue,
        bvSize = bv32Sort.sizeBits.toInt(),
        isSigned = false,
    )
}

private fun TsContext.mkFiniteIntegerInRange(
    value: UExpr<KFp64Sort>,
    minimum: Double,
    maximum: Double,
): UBoolExpr = with(this) {
    val towardZero = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero)
    val integer = mkFpRoundToIntegralExpr(
        roundingMode = towardZero,
        value = value,
    )
    val isNotNaN = mkFpIsNaNExpr(value).not()
    val isNotInfinite = mkFpIsInfiniteExpr(value).not()
    val isInteger = mkFpEqualExpr(value, integer)
    val minimumValue = mkFp64(minimum)
    val maximumValue = mkFp64(maximum)
    val isAtLeastMinimum = mkFpLessOrEqualExpr(minimumValue, value)
    val isAtMostMaximum = mkFpLessOrEqualExpr(value, maximumValue)

    mkAnd(
        isNotNaN,
        isNotInfinite,
        isInteger,
        isAtLeastMinimum,
        isAtMostMaximum,
    )
}
