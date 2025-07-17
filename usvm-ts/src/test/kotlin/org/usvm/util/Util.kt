package org.usvm.util

import kotlin.math.abs

fun Boolean.toDouble() = if (this) 1.0 else 0.0

infix fun Double.eq(other: Int): Boolean {
    return this eq other.toDouble()
}

infix fun Double.eq(other: Double): Boolean {
    return abs(this - other) < 1e-9
}
