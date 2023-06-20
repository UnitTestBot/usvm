package org.usvm.machine.operator

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.utils.BvUtils.bvMaxValueSigned
import io.ksmt.utils.BvUtils.bvMinValueSigned
import io.ksmt.utils.cast
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort

private val intSizeBits = Int.SIZE_BITS.toUInt()

@Suppress("UNCHECKED_CAST")
internal fun UExpr<out USort>.wideTo32BitsIfNeeded(signed: Boolean): UExpr<out USort> =
    with(ctx) {
        when (val sort = sort) {
            boolSort -> mkIte(this@wideTo32BitsIfNeeded as UExpr<KBoolSort>, mkBv(1, intSizeBits), mkBv(0, intSizeBits))
            is UBvSort -> {
                if (sort.sizeBits < intSizeBits) {
                    (this@wideTo32BitsIfNeeded as UExpr<UBvSort>).mkNarrow(intSizeBits.toInt(), signed)
                } else {
                    this@wideTo32BitsIfNeeded as UExpr<UBvSort>
                }
            }
            is UFpSort -> this@wideTo32BitsIfNeeded
            else -> error("unexpected sort: $sort")
        }
    }

internal fun UExpr<UBvSort>.mkNarrow(sizeBits: Int, signed: Boolean): UExpr<UBvSort> {
    val diff = sizeBits - sort.sizeBits.toInt()
    val res = if (diff > 0) {
        if (signed) {
            ctx.mkBvSignExtensionExpr(diff, this)
        } else {
            ctx.mkBvZeroExtensionExpr(diff, this)
        }
    } else {
        ctx.mkBvExtractExpr(sizeBits - 1, 0, this)
    }
    return res
}


/**
 * Performs a java-like conversion to bit-vector accordingly to
 * [Floating-Point Arithmetic](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.8)
 */
internal fun UExpr<UFpSort>.castToBv(sizeBits: Int): UExpr<UBvSort> =
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
                        mkFpRoundingModeExpr(io.ksmt.expr.KFpRoundingMode.RoundTowardZero),
                        this@castToBv,
                        sizeBits,
                        isSigned = true
                    )
                )
            )
        )
    }