package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.uctx

sealed class JcBinaryOperator(
    val onBool: UContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: UContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: UContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Add : JcBinaryOperator(
        onBv = UContext::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : JcBinaryOperator(
        onBv = UContext::mkBvSubExpr,
        onFp = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : JcBinaryOperator(
        onBv = UContext::mkBvMulExpr,
        onFp = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : JcBinaryOperator(
        onBv = UContext::mkBvSignedDivExpr,
        onFp = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Rem : JcBinaryOperator(
        onBv = UContext::mkBvSignedRemExpr,
        onFp = UContext::mkFpRemExpr, // TODO: it's incorrect. Waiting for sympfu in KSMT
    )

    object Eq : JcBinaryOperator(
        onBool = UContext::mkEq,
        onBv = UContext::mkEq,
        onFp = UContext::mkFpEqualExpr,
    )

    object Neq : JcBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    object Lt : JcBinaryOperator(
        onBv = UContext::mkBvSignedLessExpr,
        onFp = UContext::mkFpLessExpr,
    )

    object Le : JcBinaryOperator(
        onBv = UContext::mkBvSignedLessOrEqualExpr,
        onFp = UContext::mkFpLessOrEqualExpr,
    )

    object Gt : JcBinaryOperator(
        onBv = UContext::mkBvSignedGreaterExpr,
        onFp = UContext::mkFpGreaterExpr,
    )

    object Ge : JcBinaryOperator(
        onBv = UContext::mkBvSignedGreaterOrEqualExpr,
        onFp = UContext::mkFpGreaterOrEqualExpr
    )

    object And : JcBinaryOperator(
        onBool = UContext::mkAnd,
        onBv = UContext::mkBvAndExpr,
    )

    object Or : JcBinaryOperator(
        onBool = UContext::mkOr,
        onBv = UContext::mkBvOrExpr,
    )

    object Xor : JcBinaryOperator(
        onBool = UContext::mkXor,
        onBv = UContext::mkBvXorExpr,
    )

    object Cmp : JcBinaryOperator(
        onBv = { lhs, rhs ->
            mkIte(
                mkBvSignedLessExpr(lhs, rhs),
                mkBv(-1, bv32Sort),
                mkIte(
                    mkEq(lhs, rhs),
                    mkBv(0, bv32Sort),
                    mkBv(1, bv32Sort)
                )
            )
        }
    )

    object Cmpl : JcBinaryOperator(
        onFp = { lhs, rhs ->
            mkIte(
                mkOr(mkFpIsNaNExpr(lhs), mkFpIsNaNExpr(rhs)),
                mkBv(-1, bv32Sort),
                mkIte(
                    mkFpLessExpr(lhs, rhs),
                    mkBv(-1, bv32Sort),
                    mkIte(
                        mkEq(lhs, rhs),
                        mkBv(0, bv32Sort),
                        mkBv(1, bv32Sort)
                    )
                )
            )
        }
    )

    object Cmpg : JcBinaryOperator(
        onFp = { lhs, rhs ->
            mkIte(
                mkOr(mkFpIsNaNExpr(lhs), mkFpIsNaNExpr(rhs)),
                mkBv(1, bv32Sort),
                mkIte(
                    mkFpLessExpr(lhs, rhs),
                    mkBv(-1, bv32Sort),
                    mkIte(
                        mkEq(lhs, rhs),
                        mkBv(0, bv32Sort),
                        mkBv(1, bv32Sort)
                    )
                )
            )
        }
    )

    // TODO shl, shr operators

    open operator fun invoke(lhsExpr: UExpr<out USort>, rhsExpr: UExpr<out USort>): UExpr<out USort> {
        val lhs = convertBoolIfNeeded(lhsExpr, rhsExpr.sort)
        val rhs = convertBoolIfNeeded(rhsExpr, lhsExpr.sort)

        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        return when {
            lhsSort is UBoolSort && rhsSort is UBoolSort -> lhs.uctx.onBool(lhs.cast(), rhs.cast())

            lhsSort is UBvSort && rhsSort is UBvSort -> {
                if (lhsSort.sizeBits > rhsSort.sizeBits) {

                }
                lhs.uctx.onBv(lhs.cast(), rhs.cast())
            }

            lhsSort is UFpSort && rhsSort is UFpSort -> lhs.uctx.onFp(lhs.cast(), rhs.cast())

            else -> error("Expressions mismatch: $lhs, $rhs")
        }
    }

    companion object {
        private val shouldNotBeCalled: UContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}
