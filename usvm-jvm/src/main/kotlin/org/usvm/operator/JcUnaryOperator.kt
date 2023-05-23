package org.usvm.operator

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


        /**
         * TODO: Add reference here
         */
        private fun UExpr<UFpSort>.castToBv(sizeBits: Int): UExpr<UBvSort> =
            with(ctx) {
                val bvMaxValue: KExpr<KBvSort> = bvMaxValueSigned(sizeBits.toUInt()).cast()
                val bvMinValue: KExpr<KBvSort> = bvMinValueSigned(sizeBits.toUInt()).cast()
                val fpBvMaxValue = mkBvToFpExpr(sort, fpRoundingModeSortDefaultValue(), bvMaxValue, signed = true)
                val fpBvMinValue = mkBvToFpExpr(sort, fpRoundingModeSortDefaultValue(), bvMinValue, signed = true)

                mkIte(
                    mkFpIsNaNExpr(this@castToBv),
                    mkBv(0, sizeBits.toUInt()),
                    mkIte(
                        mkFpLessExpr(fpBvMaxValue, this@castToBv),
                        bvMaxValue,
                        mkIte(
                            mkFpLessExpr(this@castToBv, fpBvMinValue),
                            bvMinValue,
                            mkFpToBvExpr(
                                mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
                                this@castToBv,
                                sizeBits,
                                isSigned = true
                            )
                        )
                    )
                )
            }
    }
}



