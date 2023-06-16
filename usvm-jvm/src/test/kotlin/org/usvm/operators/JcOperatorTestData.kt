package org.usvm.operators

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import org.usvm.UExpr
import org.usvm.UFalse
import org.usvm.USort
import org.usvm.UTrue

fun extractBool(expr: UExpr<out USort>): Boolean? = when (expr) {
    is UTrue -> true
    is UFalse -> false
    else -> null
}

fun extractInt(expr: UExpr<out USort>): Int? = (expr as? KBitVec32Value)?.intValue

fun extractLong(expr: UExpr<out USort>): Long? = (expr as? KBitVec64Value)?.longValue

fun extractFloat(expr: UExpr<out USort>): Float? = (expr as? KFp32Value)?.value

fun extractDouble(expr: UExpr<out USort>): Double? = (expr as? KFp64Value)?.value


val intData = listOf(
    0,
    1,
    -1,
    2,
    -2,
    100_500,
    -100_500,
    1337,
    -1337,
    1_000_000_000,
    -1_000_000_000,
    Int.MIN_VALUE,
    Int.MAX_VALUE,
)

val longData = listOf(
    0L,
    1L,
    -1L,
    2L,
    -2L,
    100_500L,
    -100_500L,
    1337L,
    -1337L,
    1e18.toLong(),
    (-1e18).toLong(),
    Long.MIN_VALUE,
    Long.MAX_VALUE,
)

val floatData = listOf(
    0f,
    1f,
    -1f,
    2f,
    -2f,
    100_500f,
    -100_500f,
    1337f,
    -1337f,
    1_000_000_000f,
    -1_000_000_000f,
    1e18.toFloat(),
    (-1e18).toFloat(),
    Float.MIN_VALUE,
    Float.MAX_VALUE,
    Float.NaN,
    Float.NEGATIVE_INFINITY,
    Float.POSITIVE_INFINITY,
) + (Int.MAX_VALUE.toLong() - 20..Int.MAX_VALUE.toLong() + 20).map(Long::toFloat).toSet()

val doubleData = listOf(
    0.0,
    1.0,
    -1.0,
    2.0,
    -2.0,
    100_500.0,
    -100_500.0,
    1337.0,
    -1337.0,
    1_000_000_000.0,
    -1_000_000_000.0,
    Double.MIN_VALUE,
    Double.MAX_VALUE,
    Double.NaN,
    Double.NEGATIVE_INFINITY,
    Double.POSITIVE_INFINITY,
)