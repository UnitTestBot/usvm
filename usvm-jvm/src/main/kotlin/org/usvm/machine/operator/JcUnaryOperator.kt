 package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KBvSort
import io.ksmt.utils.BvUtils.bvMaxValueSigned
import io.ksmt.utils.BvUtils.bvMinValueSigned
import io.ksmt.utils.cast
import org.usvm.UBvSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.uctx

sealed class JcUnaryOperator(
    val onBv: UContext.(UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: UContext.(UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Neg : JcUnaryOperator(
        onBv = UContext::mkBvNegationExpr,
        onFp = UContext::mkFpNegationExpr,
    )

    object CastToBoolean : JcUnaryOperator(
        onBv = { operand -> operand.mkNarrow(1, signed = true).mkNarrow(Int.SIZE_BITS, signed = true) }
    )

    object CastToByte : JcUnaryOperator(
        onBv = { operand -> operand.mkNarrow(Byte.SIZE_BITS, signed = true).mkNarrow(Int.SIZE_BITS, signed = true) }
    )

    object CastToChar : JcUnaryOperator(
        onBv = { operand -> operand.mkNarrow(Char.SIZE_BITS, signed = true).mkNarrow(Int.SIZE_BITS, signed = false) }
    )

    object CastToShort : JcUnaryOperator(
        onBv = { operand -> operand.mkNarrow(Short.SIZE_BITS, signed = true).mkNarrow(Int.SIZE_BITS, signed = true) }
    )

    object CastToInt : JcUnaryOperator(
        onBv = { operand -> operand.mkNarrow(Int.SIZE_BITS, signed = true) },
        onFp = { operand -> operand.castToBv(Int.SIZE_BITS) }
    )

    object CastToLong : JcUnaryOperator(
        onBv = { operand -> operand.mkNarrow(Long.SIZE_BITS, signed = true) },
        onFp = { operand -> operand.castToBv(Long.SIZE_BITS) }
    )

    object CastToFloat : JcUnaryOperator(
        onBv = { operand -> mkBvToFpExpr(fp32Sort, fpRoundingModeSortDefaultValue(), operand, signed = true) },
        onFp = { operand -> mkFpToFpExpr(fp32Sort, fpRoundingModeSortDefaultValue(), operand) }
    )

    object CastToDouble : JcUnaryOperator(
        onBv = { operand -> mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), operand, signed = true) },
        onFp = { operand -> mkFpToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), operand) }
    )

    open operator fun invoke(operand: UExpr<out USort>): UExpr<out USort> =
        when (operand.sort) {
            is UBvSort -> operand.uctx.onBv(operand.cast())
            is UFpSort -> operand.uctx.onFp(operand.cast())
            else -> error("Expressions mismatch: $operand")
        }

    companion object {
        private val shouldNotBeCalled: UContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}
