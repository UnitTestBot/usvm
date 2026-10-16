package org.usvm.machine.expr

import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.stringCharacters
import org.usvm.util.stringLength

private const val MAX_STRING_NUMBER_CODE_UNITS = 32
private const val MAX_EXACT_DECIMAL_DIGITS = 15
private const val DECIMAL_RADIX = 10
private const val MAX_DECIMAL_DIGIT = 9

internal data class TsStringNumberConversion(val supported: UBoolExpr, val value: UExpr<KFp64Sort>)

/** Exact signed decimal integers, trimmed with ECMAScript whitespace, under the existing string bound. */
internal fun TsState.boundedIntegerStringToNumber(string: UHeapRef): TsStringNumberConversion? = with(ctx) {
    val lengthBound = boundedStringBackingRefs[string] ?: return null
    val capacity = minOf(lengthBound, MAX_STRING_NUMBER_CODE_UNITS)
    val length = stringLength(string)
    val backing = stringCharacters(string)
    val characters = (0 until capacity).map { index ->
        memory.read(mkStringBackingElementLValue(backing, mkBv(index)))
    }

    var firstIndex = length
    var lastIndex: UExpr<TsSizeSort> = mkBv(-1)
    var firstCharacter: UExpr<KBv16Sort> = mkBv(0, bv16Sort)
    for ((index, character) in characters.withIndex()) {
        val position = mkBv(index)
        val live = mkBvSignedLessExpr(position, length)
        val nonWhitespace = mkAnd(live, mkNot(mkEcmaScriptWhitespace(character)))
        val firstNonWhitespace = mkAnd(nonWhitespace, mkEq(firstIndex, length))
        firstCharacter = mkIte(firstNonWhitespace, character, firstCharacter)
        firstIndex = mkIte(firstNonWhitespace, position, firstIndex)
        lastIndex = mkIte(nonWhitespace, position, lastIndex)
    }

    val plus = mkEq(firstCharacter, mkBv('+'.code, bv16Sort))
    val minus = mkEq(firstCharacter, mkBv('-'.code, bv16Sort))
    val hasSign = mkOr(plus, minus)
    val firstDigitIndex = mkIte(hasSign, mkBvAddExpr(firstIndex, mkBv(1)), firstIndex)
    val digitCount = mkBvAddExpr(mkBvSubExpr(lastIndex, firstDigitIndex), mkBv(1))
    val empty = mkEq(firstIndex, length)
    val signOnly = mkAnd(hasSign, mkEq(digitCount, mkBv(0)))
    val digitConditions = mutableListOf<UBoolExpr>()
    var integer: UExpr<KBvSort> = mkBv(0, bv64Sort)
    val digitWidthExtension = (bv64Sort.sizeBits - bv16Sort.sizeBits).toInt()
    for ((index, character) in characters.withIndex()) {
        val position = mkBv(index)
        val inDigits = mkAnd(
            mkBvSignedGreaterOrEqualExpr(position, firstDigitIndex),
            mkBvSignedLessOrEqualExpr(position, lastIndex),
        )
        val digit = mkBvSubExpr(character, mkBv('0'.code, bv16Sort))
        val isDigit = mkBvUnsignedLessOrEqualExpr(digit, mkBv(MAX_DECIMAL_DIGIT, bv16Sort))
        digitConditions += mkOr(mkNot(inDigits), isDigit)

        val wideDigit = mkBvZeroExtensionExpr(digitWidthExtension, digit)
        val nextInteger = mkBvAddExpr(mkBvMulExpr(integer, mkBv(DECIMAL_RADIX, bv64Sort)), wideDigit)
        integer = mkIte(inDigits, nextInteger, integer)
    }

    val nonnegativeLength = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
    val lengthFitsCapacity = mkBvSignedLessOrEqualExpr(length, mkBv(capacity))
    val withinLength = mkAnd(nonnegativeLength, lengthFitsCapacity)
    val positiveDigitCount = mkBvSignedGreaterExpr(digitCount, mkBv(0))
    val digitCountFitsPrecision = mkBvSignedLessOrEqualExpr(digitCount, mkBv(MAX_EXACT_DECIMAL_DIGITS))
    val allDigits = mkAnd(digitConditions)
    val exactDigits = mkAnd(positiveDigitCount, digitCountFitsPrecision, allDigits)
    val supported = mkAnd(withinLength, mkOr(empty, signOnly, exactDigits))
    val positive = mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = integer.cast(),
        signed = false,
    )
    val signed = mkIte(minus, mkFpNegationExpr(positive), positive)
    val number = mkIte(empty, mkFp64(0.0), mkIte(signOnly, mkFp64NaN(), signed))

    TsStringNumberConversion(supported = supported, value = number)
}
