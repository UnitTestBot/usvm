package org.usvm.jacodb

import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.usvm.UExpr
import org.usvm.USort

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

class GoCall(
    val method: GoMethod,
    val entrypoint: GoInst,
    val parameters: Array<UExpr<out USort>>
)