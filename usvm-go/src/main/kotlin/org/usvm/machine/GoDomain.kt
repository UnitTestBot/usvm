package org.usvm.machine

import org.usvm.machine.type.Type

typealias GoInst = Long
typealias GoMethod = Long
typealias GoType = Long

class GoMethodInfo(
    val returnType: Type,
    val localsCount: Int,
    val parametersCount: Int,
    val parametersTypes: Array<Type>
) {
    override fun toString(): String {
        return "returnType: $returnType, params: $parametersCount, locals: $localsCount"
    }
}

class GoInstInfo(
    private val expression: String
) {
    override fun toString(): String {
        return expression
    }
}