package org.usvm.machine

import io.ksmt.utils.cast
import org.usvm.*

sealed class PandaBinaryOperator(
    val onBool: PandaContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onBv: PandaContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
    val onFp: PandaContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = { _, _ -> error("TODO") },
) {
    object Add : PandaBinaryOperator(
        onBv = PandaContext::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : PandaBinaryOperator(
        onBv = PandaContext::mkBvSubExpr,
        onFp = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : PandaBinaryOperator(
        onBv = PandaContext::mkBvMulExpr,
        onFp = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : PandaBinaryOperator(
        onBv = PandaContext::mkBvSignedDivExpr,
        onFp = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Gt : PandaBinaryOperator(
        onBv = PandaContext::mkBvSignedGreaterExpr,
        onFp = PandaContext::mkFpGreaterExpr
    )

    object Eq : PandaBinaryOperator(
        onBool = PandaContext::mkEq,
        onBv = PandaContext::mkEq,
        onFp = PandaContext::mkFpEqualExpr,
    )

    object Neq : PandaBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    internal open operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        return when {
            // TODO: JS automatic typecasts to consider (they can do ANYTHING!)
//            lhsSort != rhsSort -> error("Expressions sorts mismatch: $lhsSort, $rhsSort")

            lhsSort is UBoolSort -> lhs.pctx.onBool(lhs.cast(), rhs.cast())

            lhsSort is UBvSort -> lhs.pctx.onBv(lhs.cast(), rhs.cast())

            lhsSort is UFpSort -> lhs.pctx.onFp(lhs.cast(), rhs.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }
    }
}