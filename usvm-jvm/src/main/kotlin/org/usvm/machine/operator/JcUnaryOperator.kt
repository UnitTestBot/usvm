package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.jctx

/**
 * An util class for performing unary operations on expressions.
 */
sealed class JcUnaryOperator(
    val onBool: JcContext.(UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: JcContext.(UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: JcContext.(UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Neg : JcUnaryOperator(
        onBv = JcContext::mkBvNegationExpr,
        onFp = JcContext::mkFpNegationExpr,
    )

    object CastToBoolean : JcUnaryOperator(
        onBool = { it },
        onBv = { operand -> operand neq mkBv(0, operand.sort) }
    )

    object CastToByte : JcUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Byte.SIZE_BITS, signed = true) }
    )

    object CastToChar : JcUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Char.SIZE_BITS, signed = true) }
    )

    object CastToShort : JcUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Short.SIZE_BITS, signed = true) }
    )

    object CastToInt : JcUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Int.SIZE_BITS, signed = true) },
        onFp = { operand -> operand.castToBv(Int.SIZE_BITS) }
    )

    object CastToLong : JcUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
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

    /**
     * Performs an operation on [operand].
     *
     * @return the result expression.
     */
    open operator fun invoke(operand: UExpr<out USort>): UExpr<out USort> =
        when (operand.sort) {
            is UBoolSort -> operand.jctx.onBool(operand.cast())
            is UBvSort -> operand.jctx.onBv(operand.cast())
            is UFpSort -> operand.jctx.onFp(operand.cast())
            else -> error("Expressions mismatch: $operand")
        }

    companion object {
        private val shouldNotBeCalled: JcContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}
