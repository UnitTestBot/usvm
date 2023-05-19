package org.usvm

import io.ksmt.expr.KExpr
import io.ksmt.utils.cast

enum class JcBinOperator(
    val onBv: UContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = JcBinOperator.shouldNotBeCalled,
    val onFp: UContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = JcBinOperator.shouldNotBeCalled,
) {
    Add(
        onBv = UContext::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    ),

    Sub(
        onBv = UContext::mkBvSubExpr,
        onFp = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    ),

    Mul(
        onBv = UContext::mkBvMulExpr,
        onFp = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    ),

    Div(
        onBv = UContext::mkBvSignedDivExpr,
        onFp = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    ),

    Rem(
        onBv = UContext::mkBvSignedRemExpr,
        onFp = UContext::mkFpRemExpr,
    ),

    Eq(
        onBv = UContext::mkEq,
        onFp = UContext::mkEq,
    ),

    Neq(
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> lhs.neq(rhs) },
    ),

    Lt(
        onBv = UContext::mkBvSignedLessExpr,
        onFp = UContext::mkFpLessExpr,
    ),

    Le(
        onBv = UContext::mkBvSignedLessOrEqualExpr,
        onFp = UContext::mkFpLessOrEqualExpr,
    ),

    Gt(
        onBv = UContext::mkBvSignedGreaterExpr,
        onFp = UContext::mkFpGreaterExpr,
    ),

    Ge(
        onBv = UContext::mkBvSignedGreaterOrEqualExpr,
        onFp = UContext::mkFpGreaterOrEqualExpr
    ),

    And(
        onBv = UContext::mkBvAndExpr,
    ),

    Or(
        onBv = UContext::mkBvOrExpr,
    ),

    Xor(
        onBv = UContext::mkBvXorExpr,
    );

    open operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort
        return when {
            lhsSort is UBvSort && rhsSort is UBvSort -> {
                check(lhsSort == rhsSort) { "Sorts mismatch: $lhsSort and $rhsSort" }
                lhs.uctx.onBv(lhs.cast(), rhs.cast())
            }

            lhsSort is UFpSort && rhsSort is UFpSort -> {
                check(lhsSort == rhsSort) { "Sorts mismatch: $lhsSort and $rhsSort" }
                lhs.uctx.onFp(lhs.cast(), rhs.cast())
            }

            else -> error("Expressions mismatch: $lhs, $rhs")
        }
    }

    companion object {
        private val shouldNotBeCalled: UContext.(UExpr<out USort>, UExpr<out USort>) -> KExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}


