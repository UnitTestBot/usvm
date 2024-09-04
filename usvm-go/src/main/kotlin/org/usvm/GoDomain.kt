package org.usvm

import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.usvm.api.UnknownMethodException

class GoMethodInfo(
    val variablesCount: Int,
    val argumentsCount: Int,
) {
    override fun toString(): String {
        return "variables: $variablesCount, arguments: $argumentsCount"
    }
}

class GoInstInfo(
    private val expression: String
) {
    override fun toString(): String {
        return expression
    }
}

class GoCall(
    val method: GoMethod,
    val entrypoint: GoInst,
    val parameters: Array<UExpr<out USort>>
)

class GoPackage(
    val name: String,
    val methods: List<GoMethod>,
) {
    fun hasMethod(name: String): Boolean {
        return methods.any { it.metName == name }
    }

    fun findMethod(name: String): GoMethod {
        return methods.find { it.metName == name } ?: throw UnknownMethodException(name)
    }
}