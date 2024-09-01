package org.usvm

import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod

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
)