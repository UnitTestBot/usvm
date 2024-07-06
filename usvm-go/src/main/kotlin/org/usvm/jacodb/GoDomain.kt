package org.usvm.jacodb

import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.usvm.UExpr
import org.usvm.USort

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