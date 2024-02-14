package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import io.ksmt.utils.cast
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.GoContext
import org.usvm.machine.type.goCtx

sealed class GoUnaryOperator(
    val onBool: GoContext.(UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: GoContext.(UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: GoContext.(UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
) {
    object Neg : GoUnaryOperator(
        onBool = GoContext::mkNot,
        onBv = GoContext::mkBvNegationExpr,
        onFp = GoContext::mkFpNegationExpr,
    )

    object CastToBool : GoUnaryOperator(
        onBool = { it },
        onBv = { operand -> operand neq mkBv(0, operand.sort) }
    )

    object CastToInt8 : GoUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Byte.SIZE_BITS, signed = true) }
    )

    object CastToInt16 : GoUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Short.SIZE_BITS, signed = true) }
    )

    object CastToInt32 : GoUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Int.SIZE_BITS, signed = true) },
        onFp = { operand -> operand.castToBv(Int.SIZE_BITS) }
    )

    object CastToInt64 : GoUnaryOperator(
        onBool = { operand -> operand.wideTo32BitsIfNeeded(false) },
        onBv = { operand -> operand.mkNarrow(Long.SIZE_BITS, signed = true) },
        onFp = { operand -> operand.castToBv(Long.SIZE_BITS) }
    )

    object CastToFloat32 : GoUnaryOperator(
        onBv = { operand -> mkBvToFpExpr(fp32Sort, fpRoundingModeSortDefaultValue(), operand, signed = true) },
        onFp = { operand -> mkFpToFpExpr(fp32Sort, fpRoundingModeSortDefaultValue(), operand) }
    )

    object CastToFloat64 : GoUnaryOperator(
        onBv = { operand -> mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), operand, signed = true) },
        onFp = { operand -> mkFpToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), operand) }
    )

    open operator fun invoke(operand: UExpr<out USort>): UExpr<out USort> =
        when (operand.sort) {
            is UBoolSort -> operand.goCtx.onBool(operand.cast())
            is UBvSort -> operand.goCtx.onBv(operand.cast())
            is UFpSort -> operand.goCtx.onFp(operand.cast())
            else -> error("Expressions mismatch: $operand")
        }

    companion object {
        private val shouldNotBeCalled: GoContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }
    }
}