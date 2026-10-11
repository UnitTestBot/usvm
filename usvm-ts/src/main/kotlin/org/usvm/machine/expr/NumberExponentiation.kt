package org.usvm.machine.expr

import io.ksmt.expr.KFp64Value
import io.ksmt.sort.KFp64Sort
import org.usvm.UExpr
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope

private const val SQUARE_ROOT_EXPONENT = 0.5

/** Encodes exact modeled powers, including symbolic exponents, and reports the remaining domain as unsupported. */
fun TsContext.mkNumberExponentiationExpr(
    base: UExpr<KFp64Sort>,
    exponent: UExpr<KFp64Sort>,
    scope: TsStepScope,
): UExpr<KFp64Sort>? {
    val zero = mkFp64(value = 0.0)
    val one = mkFp64(value = 1.0)
    val two = mkFp64(value = 2.0)
    val half = mkFp64(value = SQUARE_ROOT_EXPONENT)
    val minusOne = mkFp64(value = -1.0)
    val isZeroExponent = mkFpEqualExpr(exponent, zero)
    val isOneExponent = mkFpEqualExpr(exponent, one)
    val isSquareExponent = mkFpEqualExpr(exponent, two)
    val isSquareRootExponent = mkFpEqualExpr(exponent, half)

    val isZeroBase = mkFpEqualExpr(base, zero)
    val isMinusOneExponent = mkFpEqualExpr(exponent, minusOne)
    val isZeroReciprocal = mkAnd(isZeroBase, isMinusOneExponent)
    val isMinusOneBase = mkFpEqualExpr(base, minusOne)
    val isInfiniteExponent = mkFpIsInfiniteExpr(exponent)
    val isIndeterminateUnitPower = mkAnd(isMinusOneBase, isInfiniteExponent)
    val supported = mkOr(
        isZeroExponent,
        isOneExponent,
        isSquareExponent,
        isSquareRootExponent,
        isZeroReciprocal,
        isIndeterminateUnitPower,
    )
    val concreteExponent = (exponent as? KFp64Value)?.value
    val unsupportedReason = "Number exponentiation with exponent ${concreteExponent ?: exponent} is not modeled"
    if (supported.isFalse) {
        throw UnsupportedOperationException(unsupportedReason)
    }

    // SMT floating-point arithmetic has no general pow operation. Keep the unmodeled domain visible;
    // replacing x ** -1 with 1 / x or using JVM Math.pow can differ from JavaScript by one ULP.
    scope.fork(supported, blockOnFalseState = {
        terminateAsUnsupported(reason = unsupportedReason)
    }) ?: return null

    val roundingMode = fpRoundingModeSortDefaultValue()
    val square = mkFpMulExpr(roundingMode, base, base)
    val squareRoot = mkNumberSquareRootExpr(base)
    val isNegativeBase = mkFpIsNegativeExpr(base)
    val negativeInfinity = mkFp64(value = Double.NEGATIVE_INFINITY)
    val positiveInfinity = mkFp64(value = Double.POSITIVE_INFINITY)
    val zeroReciprocal = mkIte(isNegativeBase, negativeInfinity, positiveInfinity)

    val nan = mkFp64NaN()
    val exceptionalPower = mkIte(isZeroReciprocal, zeroReciprocal, nan)
    val fractionalPower = mkIte(isSquareRootExponent, squareRoot, exceptionalPower)
    val squareOrFractionalPower = mkIte(isSquareExponent, square, fractionalPower)
    val nonZeroPower = mkIte(isOneExponent, base, squareOrFractionalPower)

    return mkIte(isZeroExponent, one, nonZeroPower)
}

private fun TsContext.mkNumberSquareRootExpr(base: UExpr<KFp64Sort>): UExpr<KFp64Sort> {
    // Number::exponentiate maps either signed zero and -Infinity to positive results for exponent 0.5.
    val positiveZero = mkFp64(value = 0.0)
    val negativeInfinity = mkFp64(value = Double.NEGATIVE_INFINITY)
    val positiveInfinity = mkFp64(value = Double.POSITIVE_INFINITY)
    val isZero = mkFpEqualExpr(base, positiveZero)
    val isNegativeInfinity = mkFpEqualExpr(base, negativeInfinity)
    val roundingMode = fpRoundingModeSortDefaultValue()
    val squareRoot = mkFpSqrtExpr(roundingMode, base)
    val ordinaryResult = mkIte(isZero, positiveZero, squareRoot)

    return mkIte(isNegativeInfinity, positiveInfinity, ordinaryResult)
}
