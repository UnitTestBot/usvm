package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSWrappedValue
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TSStepScope

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

    internal operator fun invoke(operand: UExpr<out USort>, scope: TSStepScope): UExpr<out USort> = with(operand.tctx) {
        val sort = this.desiredSort(operand.sort)
        val expr = if (operand is TSWrappedValue<*>) {
            operand.asSort(sort, scope)
        } else {
            scope.calcOnState { exprTransformer.transform(operand, sort) }
        }

        when (expr?.sort) {
            is UBoolSort -> onBool(expr.cast())
            is UBvSort -> onBv(expr.cast())
            is UFpSort -> onFp(expr.cast())
            null -> error("Expression is null")
            else -> error("Expressions mismatch: $expr")
        }
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}
