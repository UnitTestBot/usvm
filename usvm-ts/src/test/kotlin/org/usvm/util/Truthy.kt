package org.usvm.util

import org.usvm.api.TsValue

fun isTruthy(x: Double): Boolean {
    return x != 0.0 && !x.isNaN()
}

fun isTruthy(x: TsValue.TsNumber): Boolean {
    return isTruthy(x.number)
}

fun isTruthy(x: TsValue.TsClass): Boolean {
    return true
}

fun isTruthy(x: TsValue.TsObject): Boolean {
    return x.addr != 0
}
