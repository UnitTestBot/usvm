package org.usvm.util

import org.usvm.api.TsTestValue
import kotlin.math.abs

fun Boolean.toDouble() = if (this) 1.0 else 0.0

infix fun Double.eq(other: Double): Boolean {
    return abs(this - other) < 1e-9
}

infix fun Double.eq(other: Int): Boolean {
    return this eq other.toDouble()
}

infix fun Double.neq(other: Double): Boolean {
    return !(this eq other)
}

infix fun Double.neq(other: Int): Boolean {
    return !(this eq other)
}

infix fun TsTestValue.TsNumber.eq(other: Double): Boolean {
    return number eq other
}

infix fun TsTestValue.TsNumber.eq(other: Int): Boolean {
    return number eq other
}

infix fun TsTestValue.TsNumber.eq(other: TsTestValue.TsNumber): Boolean {
    return number eq other.number
}

infix fun TsTestValue.TsNumber.neq(other: Double): Boolean {
    return number neq other
}

infix fun TsTestValue.TsNumber.neq(other: Int): Boolean {
    return number neq other
}

infix fun TsTestValue.TsNumber.neq(other: TsTestValue.TsNumber): Boolean {
    return number neq other.number
}

fun TsTestValue.TsNumber.isNaN(): Boolean {
    return number.isNaN()
}
