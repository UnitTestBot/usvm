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

    object CastToInt32 : JcUnaryOperator(
        onBv = { operand -> mkBvSignExtensionExpr(INT32_SIZE - operand.sort.sizeBits.toInt(), operand) },
        onFp = { operand -> mkFpToBvExpr(fpRoundingModeSortDefaultValue(), operand, INT32_SIZE, isSigned = true) }
    )

    object CastToInt64 : JcUnaryOperator(
        onBv = { operand -> mkBvSignExtensionExpr(INT64_SIZE - operand.sort.sizeBits.toInt(), operand) },
        onFp = { operand -> mkFpToBvExpr(fpRoundingModeSortDefaultValue(), operand, INT32_SIZE, isSigned = true) }
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
        private const val INT32_SIZE = Int.SIZE_BITS
        private const val INT64_SIZE = Long.SIZE_BITS
    }
}



