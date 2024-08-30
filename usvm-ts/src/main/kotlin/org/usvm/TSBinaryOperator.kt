package org.usvm

import io.ksmt.utils.cast

sealed class TSBinaryOperator(
    val onBool: TSContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TSContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TSContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val desiredSort: TSContext.(USort, USort) -> USort = { _, _ -> error("Should not be called") }
) {

    object Eq : TSBinaryOperator(
        onBool = UContext<TSSizeSort>::mkEq,
        onBv = UContext<TSSizeSort>::mkEq,
        onFp = UContext<TSSizeSort>::mkFpEqualExpr,
        desiredSort = { lhs, _ -> lhs },
    )

    object Neq : TSBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
        desiredSort = { lhs, _ -> lhs },
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
        desiredSort = { _, _ -> fp64Sort },
    )

    internal operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        fun apply(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort>? {
            val ctx = lhs.tctx
            if (ctx.desiredSort(lhs.sort, rhs.sort) != lhs.sort) return null
            assert(lhs.sort == rhs.sort)
            return when (lhs.sort) {
                is UBoolSort -> ctx.onBool(lhs.cast(), rhs.cast())
                is UBvSort -> ctx.onBv(lhs.cast(), rhs.cast())
                is UFpSort -> ctx.onFp(lhs.cast(), rhs.cast())
                else -> error("Unexpected sorts: $lhsSort, $rhsSort")
            }
        }

        val ctx = lhs.tctx
        val sort = ctx.desiredSort(lhsSort, rhsSort)

        return when {
            lhs is TSWrappedValue -> lhs.coerceWithSort(rhs, ::apply, sort)
            else -> TSWrappedValue(ctx, lhs).coerceWithSort(rhs, ::apply, sort)
        }
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}
