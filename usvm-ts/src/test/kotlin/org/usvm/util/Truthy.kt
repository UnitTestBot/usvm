package org.usvm.util

import org.usvm.api.TsObject

fun isTruthy(x: Double): Boolean {
    return x != 0.0 && !x.isNaN()
}

fun isTruthy(x: TsObject.TsNumber): Boolean {
    return isTruthy(x.number)
}

fun isTruthy(x: TsObject.TsClass): Boolean {
    return true
}

fun isTruthy(x: TsObject.TsObject): Boolean {
    return x.addr != 0
}
