package org.usvm.machine

class GoMethodInfo(
    val parameters: Array<GoType>,
    val localsCount: Int
) {
    override fun toString(): String {
        return "params: ${parameters.contentToString()}, locals: $localsCount"
    }
}