package org.usvm.operator

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

sealed class JcBinOperator(
    val onBool: UContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: UContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: UContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Add : JcBinOperator(
        onBv = UContext::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : JcBinOperator(
        onBv = UContext::mkBvSubExpr,
        onFp = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : JcBinOperator(
        onBv = UContext::mkBvMulExpr,
        onFp = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : JcBinOperator(
        onBv = UContext::mkBvSignedDivExpr,
        onFp = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Rem : JcBinOperator(
        onBv = UContext::mkBvSignedRemExpr,
        onFp = UContext::mkFpRemExpr, // TODO: it's incorrect. Waiting for sympfu in KSMT
    )

    object Eq : JcBinOperator(
        onBool = UContext::mkEq,
        onBv = UContext::mkEq,
        onFp = UContext::mkFpEqualExpr,
    )

    object Neq : JcBinOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    object Lt : JcBinOperator(
        onBv = UContext::mkBvSignedLessExpr,
        onFp = UContext::mkFpLessExpr,
    )

    object Le : JcBinOperator(
        onBv = UContext::mkBvSignedLessOrEqualExpr,
        onFp = UContext::mkFpLessOrEqualExpr,
    )

    object Gt : JcBinOperator(
        onBv = UContext::mkBvSignedGreaterExpr,
        onFp = UContext::mkFpGreaterExpr,
    )

    object Ge : JcBinOperator(
        onBv = UContext::mkBvSignedGreaterOrEqualExpr,
        onFp = UContext::mkFpGreaterOrEqualExpr
    )

    object And : JcBinOperator(
        onBool = UContext::mkAnd,
        onBv = UContext::mkBvAndExpr,
    )

    object Or : JcBinOperator(
        onBool = UContext::mkOr,
        onBv = UContext::mkBvOrExpr,
    )

    object Xor : JcBinOperator(
        onBool = UContext::mkXor,
        onBv = UContext::mkBvXorExpr,
    )

    object Cmp : JcBinOperator(
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

    object Cmpl : JcBinOperator(
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

    object Cmpg : JcBinOperator(
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

    // TODO shl, shr

    open operator fun invoke(lhsExpr: UExpr<out USort>, rhsExpr: UExpr<out USort>): UExpr<out USort> {
        val lhs = convertBoolIfNeeded(lhsExpr, rhsExpr)
        val rhs = convertBoolIfNeeded(rhsExpr, lhs)
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort
        return when {
            lhsSort is UBoolSort && rhsSort is UBoolSort -> {
                lhs.uctx.onBool(lhs.cast(), rhs.cast())
            }

            lhsSort is UBvSort && rhsSort is UBvSort -> {
                require(lhsSort == rhsSort) { "Sorts mismatch: $lhsSort and $rhsSort" }
                require(lhsSort == lhs.uctx.bv32Sort || rhsSort == rhs.uctx.bv64Sort) { "Unexpected sort: $lhsSort" }
                lhs.uctx.onBv(lhs.cast(), rhs.cast())
            }

            lhsSort is UFpSort && rhsSort is UFpSort -> {
                require(lhsSort == rhsSort) { "Sorts mismatch: $lhsSort and $rhsSort" }
                require(lhsSort == lhs.uctx.fp32Sort || rhsSort == rhs.uctx.fp64Sort) { "Unexpected sort: $lhsSort" }
                lhs.uctx.onFp(lhs.cast(), rhs.cast())
            }

            else -> error("Expressions mismatch: $lhs, $rhs")
        }
    }

    companion object {
        private fun convertBoolIfNeeded(lhs: KExpr<out USort>, rhs: KExpr<out USort>): UExpr<out USort> {
            val lhsSort = lhs.sort
            val rhsSort = rhs.sort
            return if (lhsSort is UBoolSort && rhsSort is UBvSort) {
                with(lhs.uctx) {
                    @Suppress("UNCHECKED_CAST")
                    mkIte(lhs as UExpr<KBoolSort>, mkBv(1, rhsSort), mkBv(0, rhsSort))
                }
            } else {
                lhs
            }
        }

        private val shouldNotBeCalled: UContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}


