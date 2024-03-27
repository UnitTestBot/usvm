package org.usvm.machine

import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort

sealed class PandaBinaryOperator(
    val onBool: PandaContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onBv: PandaContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onFp: PandaContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
) {
    object Add : PandaBinaryOperator(
        onBv = PandaContext::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    internal open operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        return when {
            lhsSort != rhsSort -> error("Expressions sorts mismatch: $lhsSort, $rhsSort")

            lhsSort is UBoolSort -> lhs.pctx.onBool(lhs.cast(), rhs.cast())

            lhsSort is UBvSort -> lhs.pctx.onBv(lhs.cast(), rhs.cast())

            lhsSort is UFpSort -> lhs.pctx.onFp(lhs.cast(), rhs.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }
    }
}