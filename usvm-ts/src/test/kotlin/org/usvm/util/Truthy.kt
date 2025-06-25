package org.usvm.util

import org.usvm.api.TsTestValue

fun isTruthy(x: Double): Boolean {
    return x != 0.0 && !x.isNaN()
}

fun isTruthy(x: TsTestValue.TsNumber): Boolean {
    return isTruthy(x.number)
}

fun isTruthy(x: TsTestValue.TsClass): Boolean {
    return true
}
