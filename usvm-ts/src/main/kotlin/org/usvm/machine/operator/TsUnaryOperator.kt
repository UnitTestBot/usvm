package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.TsContext
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TsStepScope

sealed class TsUnaryOperator(
    val onBool: TsContext.(UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TsContext.(UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TsContext.(UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val desiredSort: TsContext.(USort) -> USort = { _ -> error("Should not be called") },
) {

    data object Not : TsUnaryOperator(
        onBool = TsContext::mkNot,
        desiredSort = { boolSort },
    )

    internal operator fun invoke(operand: UExpr<out USort>, scope: TsStepScope): UExpr<out USort> = with(operand.tctx) {
        TODO()
    }

    companion object {
        private val shouldNotBeCalled: TsContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}
