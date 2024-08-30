package org.usvm

import io.ksmt.expr.KExpr
import io.ksmt.utils.cast

sealed class TSUnaryOperator(
    val onBool: TSContext.(UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TSContext.(UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TSContext.(UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val desiredSort: TSContext.(USort) -> USort = { _ -> error("Should not be called") },
) {

    object Not : TSUnaryOperator(
        onBool = TSContext::mkNot,
        desiredSort = { boolSort },
    )

    internal operator fun invoke(operand: UExpr<out USort>): UExpr<out USort> = with(operand.tctx) {
        val sort = this.desiredSort(operand.sort)
        val expr = if (operand is TSWrappedValue) operand.asSort(sort) else
            TSExprTransformer(operand).transform(sort)

        when (expr.sort) {
            is UBoolSort -> onBool(expr.cast())
            is UBvSort -> onBv(expr.cast())
            is UFpSort -> onFp(expr.cast())
            else -> error("Expressions mismatch: $expr")
        }
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}
