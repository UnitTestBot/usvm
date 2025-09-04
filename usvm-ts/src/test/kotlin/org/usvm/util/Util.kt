package org.usvm.util

import org.usvm.api.TsTestValue.TsBoolean
import org.usvm.api.TsTestValue.TsNumber
import org.usvm.api.TsTestValue.TsString
import org.usvm.targets.UTarget
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

infix fun TsString.eq(other: String): Boolean {
    return value == other
}

infix fun TsString.neq(other: String): Boolean {
    return value != other
}

infix fun TsString.eq(other: TsString): Boolean {
    return eq(other.value)
}

infix fun TsString.neq(other: TsString): Boolean {
    return neq(other.value)
}

infix fun TsBoolean.eq(other: Boolean): Boolean {
    return value == other
}

infix fun TsBoolean.neq(other: Boolean): Boolean {
    return value != other
}

infix fun TsBoolean.eq(other: TsBoolean): Boolean {
    return eq(other.value)
}

infix fun TsBoolean.neq(other: TsBoolean): Boolean {
    return neq(other.value)
}

fun <T> UTarget<*, T>.getRoot(): T where T : UTarget<*, T> {
    var current = this
    while (true) {
        @Suppress("UNCHECKED_CAST")
        val parent = current.parent ?: return current as T
        current = parent
    }
}
