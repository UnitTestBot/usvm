package org.usvm.machine.operator

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.USizeSort
import org.usvm.machine.jctx
import org.usvm.uctx

/**
 * A util class for performing binary operations on expressions.
 */
sealed class JcBinaryOperator(
    val onBool: JcContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: JcContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: JcContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Add : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSubExpr,
        onFp = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvMulExpr,
        onFp = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Div : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSignedDivExpr,
        onFp = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Rem : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSignedRemExpr,
        onFp = UContext<USizeSort>::mkFpRemExpr, // TODO: it's incorrect. Waiting for sympfu in KSMT
    )

    object Eq : JcBinaryOperator(
        onBool = UContext<USizeSort>::mkEq,
        onBv = UContext<USizeSort>::mkEq,
        onFp = UContext<USizeSort>::mkFpEqualExpr,
    )

    object Neq : JcBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    object Lt : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSignedLessExpr,
        onFp = UContext<USizeSort>::mkFpLessExpr,
    )

    object Le : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSignedLessOrEqualExpr,
        onFp = UContext<USizeSort>::mkFpLessOrEqualExpr,
    )

    object Gt : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSignedGreaterExpr,
        onFp = UContext<USizeSort>::mkFpGreaterExpr,
    )

    object Ge : JcBinaryOperator(
        onBv = UContext<USizeSort>::mkBvSignedGreaterOrEqualExpr,
        onFp = UContext<USizeSort>::mkFpGreaterOrEqualExpr
    )

    object And : JcBinaryOperator(
        onBool = UContext<USizeSort>::mkAnd,
        onBv = UContext<USizeSort>::mkBvAndExpr,
    )

    object Or : JcBinaryOperator(
        onBool = UContext<USizeSort>::mkOr,
        onBv = UContext<USizeSort>::mkBvOrExpr,
    )

    object Xor : JcBinaryOperator(
        onBool = UContext<USizeSort>::mkXor,
        onBv = UContext<USizeSort>::mkBvXorExpr,
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
                        mkFpEqualExpr(lhs, rhs),
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
                        mkFpEqualExpr(lhs, rhs),
                        mkBv(0, bv32Sort),
                        mkBv(1, bv32Sort)
                    )
                )
            )
        }
    )

    object Shl : JcBinaryOperator(
        onBv = { arg, shift -> mkBvShiftLeftExpr(arg, normalizeBvShift(shift)) }
    )

    object Shr : JcBinaryOperator(
        onBv = { arg, shift -> mkBvArithShiftRightExpr(arg, normalizeBvShift(shift)) }
    )

    object Ushr : JcBinaryOperator(
        onBv = { arg, shift -> mkBvLogicalShiftRightExpr(arg, normalizeBvShift(shift)) }
    )

    /**
     * Performs an operation on [lhs] and [rhs]. A caller has to ensure, that [lhs] and [rhs] have
     * the same sorts.
     *
     * @return the result expression.
     */
    internal open operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        return when {
            lhsSort != rhsSort -> error("Expressions sorts mismatch: $lhsSort, $rhsSort")

            lhsSort is UBoolSort -> lhs.jctx.onBool(lhs.cast(), rhs.cast())

            lhsSort is UBvSort -> lhs.jctx.onBv(lhs.cast(), rhs.cast())

            lhsSort is UFpSort -> lhs.jctx.onFp(lhs.cast(), rhs.cast())

            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }
    }

    companion object {
        private val shouldNotBeCalled: JcContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }

        /**
         * Normalize binary shift value according to the specification.
         * */
        internal fun <T : UBvSort> normalizeBvShift(shift: UExpr<T>): UExpr<T> = with(shift.uctx) {
            return when (shift.sort) {
                bv32Sort -> {
                    val mask = mkBv(31) // 0b11111
                    mkBvAndExpr(shift.asExpr(bv32Sort), mask).asExpr(shift.sort)
                }

                bv64Sort -> {
                    val mask = mkBv(63L) // 0b111111
                    mkBvAndExpr(shift.asExpr(bv64Sort), mask).asExpr(shift.sort)
                }

                else -> error("Incorrect bv shift: $shift")
            }
        }
    }
}
