package org.usvm.machine.expr

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KFpRoundingMode
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.isFalse
import org.usvm.sizeSort
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.stringCharacters
import org.usvm.util.stringFromUtf16CodeUnit
import org.usvm.util.stringLength

/** String indexing reads one UTF-16 code unit, or undefined for every non-index property number. */
internal fun TsExprResolver.readStringIndex(receiver: UHeapRef, index: UExpr<*>): UExpr<*>? = with(ctx) {
    require(index.sort == fp64Sort) { "String indexing needs a numeric property key" }

    val lengthBound = scope.calcOnState { boundedStringBackingRefs[receiver] }
        ?: throw UnsupportedOperationException("String indexing needs modeled backing for $receiver")
    val length = scope.calcOnState { stringLength(receiver) }
    ensureLengthBounds(scope = scope, length = length, maxLength = lengthBound) ?: return null

    val number = index.asExpr(fp64Sort)
    val integer = mkFpRoundToIntegralExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
        value = number,
    )
    val fpLength = mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = length.cast(),
        signed = true,
    )
    val zero = mkFp64(0.0)
    val isIndex = mkAnd(
        mkFpEqualExpr(number, integer),
        mkFpGreaterOrEqualExpr(number, zero),
        mkFpLessExpr(number, fpLength),
    )
    if (isIndex.isFalse) {
        return mkUndefinedValue()
    }

    val position = mkFpToBvExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
        value = number,
        bvSize = sizeSort.sizeBits.toInt(),
        isSigned = true,
    ).asExpr(sizeSort)
    val character = scope.calcOnState {
        val characters = stringCharacters(receiver)
        val codeUnit = memory.read(mkStringBackingElementLValue(ref = characters, index = position))

        if (codeUnit is KBitVec16Value) {
            mkInitializedStringConstant(codeUnit.shortValue.toInt().toChar().toString())
        } else {
            stringFromUtf16CodeUnit(codeUnit)
        }
    }

    mkIte(isIndex, character, mkUndefinedValue())
}
