package org.usvm.util

import org.usvm.api.TSObject

fun isTruthy(x: Double): Boolean {
    return x != 0.0 && !x.isNaN()
}

fun isTruthy(x: TSObject.TSNumber): Boolean {
    return isTruthy(x.number)
}

fun isTruthy(x: TSObject.TSClass): Boolean {
    return true
}

fun isTruthy(x: TSObject.TSObject): Boolean {
    return x.addr != 0
}
