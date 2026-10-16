package org.usvm.machine.expr

import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.stringCharacters
import org.usvm.util.stringLength

private val ecmaScriptWhitespace = listOf(
    '\t', '\n', '\u000b', '\u000c', '\r', ' ', '\u00a0', '\u1680',
    '\u2028', '\u2029', '\u202f', '\u205f', '\u3000', '\ufeff',
) + ('\u2000'..'\u200a')

/** ECMAScript WhiteSpace and LineTerminator, kept as one predicate rather than execution branches. */
internal fun TsContext.mkEcmaScriptWhitespace(codeUnit: UExpr<KBv16Sort>): UBoolExpr {
    val matches = ecmaScriptWhitespace.map { character ->
        mkEq(codeUnit, mkBv(character.code, bv16Sort))
    }

    return mkOr(matches)
}

/** ToNumber for the zero- or one-code-unit Strings produced by indexing and charAt. */
internal fun TsContext.singleCodeUnitToNumber(scope: TsStepScope, string: UHeapRef): UExpr<KFp64Sort>? {
    val lengthBound = scope.calcOnState { boundedStringBackingRefs[string] } ?: return null
    if (lengthBound !in 0..1) {
        return null
    }

    val (length, codeUnit) = scope.calcOnState {
        val characters = stringCharacters(string)
        val character = mkStringBackingElementLValue(ref = characters, index = mkBv(0))

        stringLength(string) to memory.read(character)
    }
    val firstDigit = mkBv('0'.code, bv16Sort)
    val lastDigit = mkBv('9'.code, bv16Sort)
    val isDigit = mkAnd(
        mkBvUnsignedGreaterOrEqualExpr(codeUnit, firstDigit),
        mkBvUnsignedLessOrEqualExpr(codeUnit, lastDigit),
    )
    val digit = mkBvSubExpr(codeUnit, firstDigit)
    val digitNumber = mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = digit.cast(),
        signed = false,
    )
    val isWhitespace = mkEcmaScriptWhitespace(codeUnit)
    val zero = mkFp64(0.0)
    val nonDigitNumber = mkIte(isWhitespace, zero, mkFp64NaN())
    val nonEmptyNumber = mkIte(isDigit, digitNumber, nonDigitNumber)
    val emptyLength = mkBv(0)
    val isEmpty = mkEq(length, emptyLength)

    return mkIte(isEmpty, zero, nonEmptyNumber)
}
