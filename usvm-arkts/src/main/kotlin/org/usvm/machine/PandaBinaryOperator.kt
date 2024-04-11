package org.usvm.machine

import io.ksmt.utils.cast
import org.jacodb.panda.dynamic.api.PandaNumberConstant
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
        onBool = { lhs, rhs -> TODO() },
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

    internal open operator fun invoke(lhs: PandaUExprWrapper, rhs: PandaUExprWrapper): UExpr<out USort> {
        val lhsUExpr = lhs.uExpr
        var rhsUExpr = rhs.uExpr
        if (lhsUExpr.sort != rhsUExpr.sort) {
            rhsUExpr = rhs.withSort(lhsUExpr.ctx as PandaContext, lhsUExpr.sort).uExpr
        }

        val lhsSort = lhsUExpr.sort
        val rhsSort = rhsUExpr.sort

        return when {

            lhsSort is UBoolSort -> lhsUExpr.pctx.onBool(lhsUExpr.cast(), rhsUExpr.cast())

            lhsSort is UBvSort -> lhsUExpr.pctx.onBv(lhsUExpr.cast(), rhsUExpr.cast())

            lhsSort is UFpSort -> lhsUExpr.pctx.onFp(lhsUExpr.cast(), rhsUExpr.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }

    }
}

fun PandaUExprWrapper.withSort(ctx: PandaContext, sort: USort): PandaUExprWrapper {
    val newUExpr = when(from) {
        is PandaNumberConstant -> from.withSort(ctx, sort, uExpr)
        else -> uExpr
    }

    return PandaUExprWrapper(from, newUExpr)
}

fun PandaNumberConstant.withSort(
    ctx: PandaContext,
    sort: USort,
    default: UExpr<out USort>
): UExpr<out USort> = when(sort) {
    is PandaBoolSort -> ctx.mkBool(value == 1)
    else -> default
}