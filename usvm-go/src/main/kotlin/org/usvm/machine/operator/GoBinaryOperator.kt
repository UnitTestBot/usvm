package org.usvm.machine.operator

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.GoContext
import org.usvm.machine.type.goCtx
import org.usvm.uctx

sealed class GoBinaryOperator(
    val onBool: GoContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: GoContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: GoContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Add : GoBinaryOperator(
        onBv = GoContext::mkBvAddExpr,
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Sub : GoBinaryOperator(
        onBv = GoContext::mkBvSubExpr,
        onFp = { lhs, rhs -> mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    object Mul : GoBinaryOperator(
        onBv = GoContext::mkBvMulExpr,
        onFp = { lhs, rhs -> mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    class Div(signed: Boolean) : GoBinaryOperator(
        onBv = if (signed) GoContext::mkBvSignedDivExpr else GoContext::mkBvUnsignedDivExpr,
        onFp = { lhs, rhs -> mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) }
    )

    class Rem(signed: Boolean) : GoBinaryOperator(
        onBv = if (signed) GoContext::mkBvSignedRemExpr else GoContext::mkBvUnsignedRemExpr,
        onFp = GoContext::mkFpRemExpr,
    )

    object And : GoBinaryOperator(
        onBool = GoContext::mkAnd,
        onBv = GoContext::mkBvAndExpr,
    )

    object Or : GoBinaryOperator(
        onBool = GoContext::mkOr,
        onBv = GoContext::mkBvOrExpr,
    )

    object Xor : GoBinaryOperator(
        onBool = GoContext::mkXor,
        onBv = GoContext::mkBvXorExpr,
    )

    object Shl : GoBinaryOperator(
        onBv = { arg, shift -> mkBvShiftLeftExpr(arg, normalizeBvShift(shift)) }
    )

    class Shr(signed: Boolean) : GoBinaryOperator(
        onBv = { arg, shift ->
            if (signed) mkBvArithShiftRightExpr(arg, normalizeBvShift(shift))
            else mkBvLogicalShiftRightExpr(arg, normalizeBvShift(shift))
        }
    )

    object AndNot : GoBinaryOperator(
        onBv = { lhs, rhs -> mkBvAndExpr(lhs, mkBvNegationExpr(rhs)) }
    )

    object Eq : GoBinaryOperator(
        onBool = GoContext::mkEq,
        onBv = GoContext::mkEq,
        onFp = GoContext::mkFpEqualExpr,
    )

    class Lt(signed: Boolean) : GoBinaryOperator(
        onBv = if (signed) GoContext::mkBvSignedLessExpr else GoContext::mkBvUnsignedLessExpr,
        onFp = GoContext::mkFpLessExpr,
    )

    class Gt(signed: Boolean) : GoBinaryOperator(
        onBv = if (signed) GoContext::mkBvSignedGreaterExpr else GoContext::mkBvUnsignedGreaterExpr,
        onFp = GoContext::mkFpGreaterExpr,
    )

    object Neq : GoBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
    )

    class Le(signed: Boolean) : GoBinaryOperator(
        onBv = if (signed) GoContext::mkBvSignedLessOrEqualExpr else GoContext::mkBvUnsignedLessOrEqualExpr,
        onFp = GoContext::mkFpLessOrEqualExpr,
    )

    class Ge(signed: Boolean) : GoBinaryOperator(
        onBv = if (signed) GoContext::mkBvSignedGreaterOrEqualExpr else GoContext::mkBvUnsignedGreaterOrEqualExpr,
        onFp = GoContext::mkFpGreaterOrEqualExpr
    )

    internal open operator fun invoke(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
        val lhsSort = lhs.sort
        val rhsSort = rhs.sort

        return when {
            lhsSort != rhsSort -> error("Expressions sorts mismatch: $lhsSort, $rhsSort")
            lhsSort is UBoolSort -> lhs.goCtx.onBool(lhs.cast(), rhs.cast())
            lhsSort is UBvSort -> lhs.goCtx.onBv(lhs.cast(), rhs.cast())
            lhsSort is UFpSort -> lhs.goCtx.onFp(lhs.cast(), rhs.cast())
            else -> error("Unexpected sorts: $lhsSort, $rhsSort")
        }
    }

    companion object {
        private val shouldNotBeCalled: GoContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }

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
