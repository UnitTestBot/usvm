package org.usvm.machine.operator

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.utils.BvUtils.bvMaxValueSigned
import io.ksmt.utils.BvUtils.bvMinValueSigned
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.mkNarrow

private val intSizeBits = Int.SIZE_BITS.toUInt()

/**
 * Widens a bit-vec expression to be at least of [intSizeBits] size regarding [signed] flag. Converts booleans to
 * bit-vec expressions of a [intSizeBits] size
 *
 * @return the bit-vec expression of [intSizeBits] size.
 */
@Suppress("UNCHECKED_CAST")
internal fun UExpr<out USort>.wideTo32BitsIfNeeded(signed: Boolean): UExpr<out USort> =
    with(ctx) {
        when (val sort = sort) {
            boolSort -> mkIte(
                this@wideTo32BitsIfNeeded as UExpr<KBoolSort>,
                mkBv(1, intSizeBits),
                mkBv(0, intSizeBits)
            )

            is UBvSort -> {
                if (sort.sizeBits < intSizeBits) {
                    (this@wideTo32BitsIfNeeded as UExpr<UBvSort>).mkNarrow(intSizeBits.toInt(), signed)
                } else {
                    this@wideTo32BitsIfNeeded as UExpr<UBvSort>
                }
            }

            is UFpSort -> this@wideTo32BitsIfNeeded
            else -> error("Unexpected sort: $sort")
        }
    }

/**
 * Performs checked cast of any UExpr to bit-vec expression.
 * */
@Suppress("UNCHECKED_CAST")
internal fun UExpr<*>.ensureBvExpr(): UExpr<UBvSort> {
    check(sort is UBvSort) { "$this is not a Bv expr" }
    return this as UExpr<UBvSort>
}

/**
 * Performs a java-like conversion to bit-vector accordingly to
 * [Floating-Point Arithmetic](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.8)
 */
internal fun UExpr<UFpSort>.castToBv(sizeBits: Int): UExpr<UBvSort> =
    with(ctx) {
        val bvMaxValue = bvMaxValueSigned<KBvSort>(sizeBits.toUInt())
        val bvMinValue = bvMinValueSigned<KBvSort>(sizeBits.toUInt())
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