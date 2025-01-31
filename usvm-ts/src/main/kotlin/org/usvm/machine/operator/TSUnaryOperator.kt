package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.TSContext
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TSStepScope

sealed class TSUnaryOperator(
    val onBool: TSContext.(UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TSContext.(UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TSContext.(UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val desiredSort: TSContext.(USort) -> USort = { _ -> error("Should not be called") },
) {

    data object Not : TSUnaryOperator(
        onBool = TSContext::mkNot,
        desiredSort = { boolSort },
    )

    @Suppress("UNUSED_PARAMETER")
    internal operator fun invoke(operand: UExpr<out USort>, scope: TSStepScope): UExpr<out USort> = with(operand.tctx) {
        TODO()
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}
