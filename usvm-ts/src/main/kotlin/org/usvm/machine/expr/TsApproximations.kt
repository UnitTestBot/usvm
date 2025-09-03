package org.usvm.machine.expr

import org.usvm.UExpr

sealed interface TsExprApproximationResult {
    data class SuccessfulApproximation(val expr: UExpr<*>) : TsExprApproximationResult
    data object ResolveFailure : TsExprApproximationResult
    data object NoApproximation : TsExprApproximationResult

    companion object {
        fun from(expr: UExpr<*>?): TsExprApproximationResult = when {
            expr != null -> SuccessfulApproximation(expr)
            else -> ResolveFailure
        }
    }
}
