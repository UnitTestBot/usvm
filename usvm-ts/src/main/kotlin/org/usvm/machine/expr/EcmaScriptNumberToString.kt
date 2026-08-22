package org.usvm.machine.expr

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.absoluteValue

private const val MAX_SIGNIFICANT_DIGITS = 17
private const val MAX_PLAIN_DECIMAL_POINT = 21
private const val MIN_PLAIN_DECIMAL_POINT = -5
private const val ROUNDING_NEIGHBORHOOD = 2

/** Formats a concrete IEEE-754 value according to ECMAScript Number::toString. */
internal fun Double.toEcmaScriptString(): String = when {
    isNaN() -> "NaN"
    this == Double.POSITIVE_INFINITY -> "Infinity"
    this == Double.NEGATIVE_INFINITY -> "-Infinity"
    this == 0.0 -> "0"
    else -> {
        val negative = this < 0.0
        val decimal = absoluteValue.shortestRoundTripDecimal().stripTrailingZeros()
        val digits = decimal.unscaledValue().abs().toString()
        val decimalPoint = digits.length - decimal.scale()
        val unsigned = when {
            decimalPoint in 1..MAX_PLAIN_DECIMAL_POINT -> {
                if (decimalPoint >= digits.length) {
                    digits + "0".repeat(decimalPoint - digits.length)
                } else {
                    digits.substring(0, decimalPoint) + "." + digits.substring(decimalPoint)
                }
            }

            decimalPoint in MIN_PLAIN_DECIMAL_POINT..0 ->
                "0." + "0".repeat(-decimalPoint) + digits

            else -> {
                val mantissa = if (digits.length == 1) {
                    digits
                } else {
                    digits.substring(0, 1) + "." + digits.substring(1)
                }
                val exponent = decimalPoint - 1
                mantissa + "e" + (if (exponent >= 0) "+" else "") + exponent
            }
        }
        if (negative) "-$unsigned" else unsigned
    }
}

private fun Double.shortestRoundTripDecimal(): BigDecimal {
    // This constructor intentionally retains the exact binary value. At the first
    // precision that round-trips, ECMAScript selects the closest decimal (ties to even).
    val exact = BigDecimal(this)
    for (precision in 1..MAX_SIGNIFICANT_DIGITS) {
        val rounded = exact.round(MathContext(precision, RoundingMode.HALF_EVEN))
        val unit = rounded.ulp()
        val candidates = (-ROUNDING_NEIGHBORHOOD..ROUNDING_NEIGHBORHOOD)
            .asSequence()
            .map { offset -> rounded + unit * offset.toBigDecimal() }
            .filter { candidate -> candidate.signum() > 0 && candidate.toDouble() == this }
            .toList()
        if (candidates.isNotEmpty()) {
            return candidates.minWith(
                compareBy<BigDecimal> { candidate -> candidate.subtract(exact).abs() }
                    .thenBy { candidate -> candidate.unscaledValue().abs().and(BigInteger.ONE).toInt() }
                    .thenBy { candidate -> candidate },
            )
        }
    }
    error("Could not format finite double: $this")
}
