package org.usvm

import io.ksmt.utils.cast

sealed class TSBinaryOperator(
    val onBool: TSContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TSContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TSContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onRef: TSContext.(UExpr<UAddressSort>, UExpr<UAddressSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val desiredSort: TSContext.(USort, USort) -> USort = { _, _ -> error("Should not be called") },
    val banSorts: TSContext.(UExpr<out USort>, UExpr<out USort>) -> Set<USort> = {_, _ -> emptySet() },
) {

    object Eq : TSBinaryOperator(
        onBool = UContext<TSSizeSort>::mkEq,
        onBv = UContext<TSSizeSort>::mkEq,
        onFp = UContext<TSSizeSort>::mkFpEqualExpr,
        onRef = UContext<TSSizeSort>::mkEq,
        desiredSort = { lhs, _ -> lhs }
    )

    object Neq : TSBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
        onRef = { lhs, rhs -> lhs.neq(rhs) },
        desiredSort = { lhs, _ -> lhs },
        banSorts = { lhs, rhs ->
            when {
                lhs is TSWrappedValue ->
                    // rhs.sort == addressSort is a mock not to cause undefined
                    // behaviour with support of new language features.
                    if (rhs is TSWrappedValue || rhs.sort == addressSort) emptySet() else TSTypeSystem.primitiveTypes
                        .map(::typeToSort).toSet()
                        .minus(rhs.sort)
                rhs is TSWrappedValue ->
                    // lhs.sort == addressSort explained as above.
                    if (lhs.sort == addressSort) emptySet() else TSTypeSystem.primitiveTypes
                        .map(::typeToSort).toSet()
                        .minus(lhs.sort)
                else -> emptySet()
            }
        }
    )

    object Add : TSBinaryOperator(
        onBool = { lhs, rhs ->
            mkFpAddExpr(
                fpRoundingModeSortDefaultValue(),
                boolToFpSort(lhs),
                boolToFpSort(rhs)
            )
        },
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) },
        onBv = UContext<TSSizeSort>::mkBvAddExpr,
        desiredSort = { _, _ -> fp64Sort },
    )

    object And : TSBinaryOperator(
        onBool = UContext<TSSizeSort>::mkAnd,
        onBv = UContext<TSSizeSort>::mkBvAndExpr,
        desiredSort = { _, _ -> boolSort },
    )

    internal operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>, scope: TSStepScope): UExpr<out USort> {
        val bannedSorts = lhs.tctx.banSorts(lhs, rhs)

        fun apply(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort>? {
            val ctx = lhs.tctx
            val lhsSort = lhs.sort
            val rhsSort = rhs.sort
            assert(lhsSort == rhsSort)

            if (lhsSort in bannedSorts) return null
            if (ctx.desiredSort(lhsSort, rhsSort) != lhsSort) return null

            return when (lhs.sort) {
                is UBoolSort -> ctx.onBool(lhs.cast(), rhs.cast())
                is UBvSort -> ctx.onBv(lhs.cast(), rhs.cast())
                is UFpSort -> ctx.onFp(lhs.cast(), rhs.cast())
                is UAddressSort -> ctx.onRef(lhs.cast(), rhs.cast())
                else -> error("Unexpected sorts: $lhsSort, $rhsSort")
            }
        }

        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        val ctx = lhs.tctx
        val sort = ctx.desiredSort(lhsSort, rhsSort)

        return when {
            lhs is TSWrappedValue -> lhs.coerceWithSort(rhs, ::apply, sort)
            else -> TSWrappedValue(ctx, lhs, scope).coerceWithSort(rhs, ::apply, sort)
        }
    }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}
