package org.usvm.util

import org.usvm.api.TsTestValue.TsNumber
import kotlin.math.absoluteValue

fun Boolean.toDouble() = if (this) 1.0 else 0.0

infix fun Double.eq(other: Double): Boolean {
    return (this - other).absoluteValue <= Double.MIN_VALUE
}

infix fun Double.neq(other: Double): Boolean {
    return !(this eq other)
}

infix fun TsNumber.eq(other: TsNumber): Boolean {
    return number eq other.number
}

infix fun TsNumber.neq(other: TsNumber): Boolean {
    return number neq other.number
}

infix fun TsNumber.eq(other: Double): Boolean {
    return number eq other
}

infix fun TsNumber.neq(other: Double): Boolean {
    return number neq other
}

infix fun TsNumber.eq(other: Int): Boolean {
    return number eq other.toDouble()
}

infix fun TsNumber.neq(other: Int): Boolean {
    return number neq other.toDouble()
}

fun TsNumber.isNaN(): Boolean {
    return number.isNaN()
}
