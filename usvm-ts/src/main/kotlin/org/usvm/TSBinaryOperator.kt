package org.usvm

import io.ksmt.sort.KFp64Sort
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

    object Add : TSBinaryOperator(
        onBool = { lhs, rhs ->
            mkFpAddExpr(
                fpRoundingModeSortDefaultValue(),
                TSExprTransformer(lhs).asFp64(),
                TSExprTransformer(rhs).asFp64())
        },
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) },
        onBv = UContext<TSSizeSort>::mkBvAddExpr,
    )

    internal operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        fun apply(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
            assert(lhs.sort == rhs.sort)
            val ctx = lhs.tctx
            return when (lhs.sort) {
                is UBoolSort -> ctx.onBool(lhs.cast(), rhs.cast())
                is UBvSort -> ctx.onBv(lhs.cast(), rhs.cast())
                is UFpSort -> ctx.onFp(lhs.cast(), rhs.cast())
                else -> error("Unexpected sorts: $lhsSort, $rhsSort")
            }
        }

        return when {
            lhs is TSWrappedValue -> lhs.coerce(rhs, ::apply)
            rhs is TSWrappedValue -> TSWrappedValue(lhs.tctx, lhs).coerce(rhs, ::apply)
            lhsSort != rhsSort -> apply(lhs, TSExprTransformer(rhs).transform(lhsSort))
            else -> apply(lhs, rhs)
        }
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}
