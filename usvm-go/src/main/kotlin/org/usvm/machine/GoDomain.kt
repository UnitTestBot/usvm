package org.usvm.machine

typealias GoInst = Long
typealias GoMethod = Long
typealias GoType = Long

class GoMethodInfo(
    val parametersCount: Int,
    val localsCount: Int
) {
    override fun toString(): String {
        return "params: ${parametersCount}, locals: $localsCount"
    }
}