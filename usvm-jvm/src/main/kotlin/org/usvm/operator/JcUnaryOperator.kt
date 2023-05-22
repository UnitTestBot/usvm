package org.usvm.operator

import io.ksmt.expr.KExpr
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
        onFp = { operand -> mkFpToBvExpr(fpRoundingModeSortDefaultValue(), operand, Int.SIZE_BITS, isSigned = true) }
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

    open operator fun invoke(operand: UExpr<out USort>): UExpr<out USort> {
        val sort = operand.sort
        return when {
            sort is UBvSort -> {
                operand.uctx.onBv(operand.cast())
            }

            sort is UFpSort -> {
                operand.uctx.onFp(operand.cast())
            }

            else -> error("Expressions mismatch: $operand")
        }
    }

    companion object {
        private val shouldNotBeCalled: UContext.(UExpr<out USort>) -> KExpr<out USort> =
            { _ -> error("Should not be called") }

        private fun UExpr<UBvSort>.mkNarrow(sizeBits: Int, signed: Boolean): UExpr<UBvSort> {
            val diff = sizeBits - sort.sizeBits.toInt()
            val res = if (diff > 0) {
                if (!signed) {
                    ctx.mkBvZeroExtensionExpr(diff, this)
                } else {
                    ctx.mkBvSignExtensionExpr(diff, this)
                }
            } else {
                ctx.mkBvExtractExpr(sizeBits - 1, 0, this)
            }
            return res
        }

        private fun UExpr<UFpSort>.castToBv(sizeBits: Int): UExpr<UBvSort> =
            with(ctx) {
                mkIte(
                    mkFpIsNaNExpr(this@castToBv),
                    mkBv(0, sizeBits.toUInt()),
                    mkFpToBvExpr(fpRoundingModeSortDefaultValue(), this@castToBv, sizeBits, isSigned = true)
                ) // TODO: more branches here covering infinity and [MIN_VALUE, MAX_VALUE]
            }
    }
}



