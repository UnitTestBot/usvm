package org.usvm.util

class GoResult(
    val message: String,
    val code: Int
) {
    override fun toString(): String {
        return "$message: (code: $code)"
    }
}