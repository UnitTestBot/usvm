package org.usvm.domain

class GoMethodInfo(
    val parametersCount: Int,
    val localsCount: Int
) {
    override fun toString(): String {
        return "params: ${parametersCount}, locals: $localsCount"
    }
}