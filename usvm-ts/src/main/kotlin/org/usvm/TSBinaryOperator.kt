package org.usvm

import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsAnyType

sealed class TSBinaryOperator(
    val onBool: TSContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TSContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TSContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {

    object Eq : TSBinaryOperator(
        onBool = UContext<TSSizeSort>::mkEq,
        onBv = UContext<TSSizeSort>::mkEq,
        onFp = UContext<TSSizeSort>::mkFpEqualExpr,
    )

    object Neq : TSBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    internal operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>, scope: TSStepScope): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort
        var rhsExpr: UExpr<out USort> = rhs

        if (lhsSort != rhsSort) {
            val (temp, type) = TSExprTransformer(rhs).transform(lhs)
            rhsExpr = temp
            if (type !is EtsAnyType) {
                scope.fork(
                    condition = scope.calcOnState { memory.types.evalIsSubtype(rhsExpr.cast(), type) },
                    blockOnTrueState = {
                        scope.calcOnState {  }
                    }

                )
            }
        }

        return when {
            lhsSort is UBoolSort -> lhs.tctx.onBool(lhs.cast(), rhsExpr.cast())
            lhsSort is UBvSort -> lhs.tctx.onBv(lhs.cast(), rhsExpr.cast())
            lhsSort is UFpSort -> lhs.tctx.onFp(lhs.cast(), rhsExpr.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}
