package org.usvm.machine

import org.usvm.machine.type.GoType

typealias GoInst = Long
typealias GoMethod = Long

class GoMethodInfo(
    val returnType: GoType,
    val variablesCount: Int,
    val parametersCount: Int,
    val parametersTypes: Array<GoType>
) {
    override fun toString(): String {
        return "returnType: $returnType, variables: $variablesCount, params: $parametersCount, params types: ${parametersTypes.contentToString()}"
    }
}

class GoInstInfo(
    private val expression: String
) {
    override fun toString(): String {
        return expression
    }
}