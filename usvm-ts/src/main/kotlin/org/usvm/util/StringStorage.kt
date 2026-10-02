package org.usvm.util

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.initializeArrayLength
import org.usvm.api.memcpy
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState
import org.usvm.sizeSort

internal val STRING_CHARACTER_ARRAY_TYPE = EtsArrayType(EtsNumberType, dimensions = 1)

internal fun TsState.stringCharacters(receiver: UHeapRef): UHeapRef = with(ctx) {
    memory.read(mkFieldLValue(addressSort, receiver, "value")).asExpr(addressSort)
}

internal fun TsState.stringLength(receiver: UHeapRef): UExpr<TsSizeSort> {
    val characters = stringCharacters(receiver)
    return memory.read(mkArrayLengthLValue(characters, STRING_CHARACTER_ARRAY_TYPE))
}

fun TsState.markStringMaxLength(
    string: UConcreteHeapRef,
    maxLength: Int,
) {
    require(maxLength >= 0) { "String maximum length must be non-negative" }
    stringMaxLengths[string] = maxLength
}

private fun TsState.concreteStringMaxLength(string: UHeapRef): Int? =
    (string as? UConcreteHeapRef)?.let(stringMaxLengths::get)

private data class StringLengthBound(
    val representative: UHeapRef,
    val maxLength: Int,
    val isTrackedString: UBoolExpr,
)

private fun TsState.stringLengthBound(string: UHeapRef): StringLengthBound? = with(ctx) {
    concreteStringMaxLength(string)?.let { maxLength ->
        return StringLengthBound(
            representative = string,
            maxLength = maxLength,
            isTrackedString = trueExpr,
        )
    }
    if (string is UConcreteHeapRef || stringMaxLengths.isEmpty()) {
        return null
    }

    val trackedStrings = stringMaxLengths.entries.toList()
    val representative = trackedStrings.drop(1).fold(trackedStrings.first().key as UHeapRef) { fallback, entry ->
        mkIte(
            condition = mkHeapRefEq(string, entry.key),
            trueBranch = entry.key,
            falseBranch = fallback,
        )
    }
    val trackedStringGuards = trackedStrings.map { (tracked, _) -> mkHeapRefEq(string, tracked) }
    StringLengthBound(
        representative = representative,
        maxLength = trackedStrings.maxOf { it.value },
        isTrackedString = mkOr(trackedStringGuards),
    )
}

internal fun TsState.allocateString(
    length: UExpr<TsSizeSort>,
    maxLength: Int? = null,
): Pair<UConcreteHeapRef, UConcreteHeapRef> = with(ctx) {
    val string = memory.allocConcrete(EtsStringType)
    val characters = memory.allocConcrete(STRING_CHARACTER_ARRAY_TYPE.elementType)
    memory.initializeArrayLength(
        arrayHeapRef = characters,
        type = arrayDescriptorOf(STRING_CHARACTER_ARRAY_TYPE),
        sizeSort = sizeSort,
        count = length,
    )
    memory.write(
        mkFieldLValue(addressSort, string, "value"),
        characters,
        guard = trueExpr,
    )
    if (maxLength != null) {
        markStringMaxLength(string = string, maxLength = maxLength)
    }

    string to characters
}

internal fun TsState.stringValueEqualsOrNull(
    left: UHeapRef,
    right: UHeapRef,
): UBoolExpr? = with(ctx) {
    val leftBound = stringLengthBound(left) ?: return null
    val rightBound = stringLengthBound(right) ?: return null
    val leftCharacters = stringCharacters(leftBound.representative)
    val rightCharacters = stringCharacters(rightBound.representative)
    val leftLength = memory.read(mkArrayLengthLValue(leftCharacters, STRING_CHARACTER_ARRAY_TYPE))
    val rightLength = memory.read(mkArrayLengthLValue(rightCharacters, STRING_CHARACTER_ARRAY_TYPE))
    val zeroLength = mkBv(0)
    val leftMaximumLength = mkBv(leftBound.maxLength)
    val rightMaximumLength = mkBv(rightBound.maxLength)
    val lengthIsValid = mkAnd(
        leftBound.isTrackedString,
        mkBvSignedGreaterOrEqualExpr(leftLength, zeroLength),
        mkBvSignedLessOrEqualExpr(leftLength, leftMaximumLength),
        rightBound.isTrackedString,
        mkBvSignedGreaterOrEqualExpr(rightLength, zeroLength),
        mkBvSignedLessOrEqualExpr(rightLength, rightMaximumLength),
    )
    val lengthsAreEqual = mkEq(leftLength, rightLength)
    val charactersAreEqual = (0 until maxOf(leftBound.maxLength, rightBound.maxLength)).map { index ->
        val symbolicIndex = mkBv(index)
        val indexIsLive = mkBvSignedLessExpr(symbolicIndex, leftLength)
        val leftCharacter = memory.read(
            mkArrayIndexLValue(
                sort = bv16Sort,
                ref = leftCharacters,
                index = symbolicIndex,
                type = STRING_CHARACTER_ARRAY_TYPE,
            )
        )
        val rightCharacter = memory.read(
            mkArrayIndexLValue(
                sort = bv16Sort,
                ref = rightCharacters,
                index = symbolicIndex,
                type = STRING_CHARACTER_ARRAY_TYPE,
            )
        )

        mkImplies(indexIsLive, mkEq(leftCharacter, rightCharacter))
    }

    mkAnd(lengthIsValid, lengthsAreEqual, mkAnd(charactersAreEqual))
}

internal fun TsState.refOrStringValueEquals(
    left: UHeapRef,
    right: UHeapRef,
): UBoolExpr = with(ctx) {
    val referencesAreEqual = mkHeapRefEq(left, right)
    val stringValuesAreEqual = stringValueEqualsOrNull(left, right) ?: return referencesAreEqual
    val bothAreStrings = mkAnd(
        memory.types.evalTypeEquals(left, EtsStringType),
        memory.types.evalTypeEquals(right, EtsStringType),
    )

    mkOr(referencesAreEqual, mkAnd(bothAreStrings, stringValuesAreEqual))
}

internal fun TsState.refOrStringTruthy(ref: UHeapRef): UBoolExpr = with(ctx) {
    val referenceIsTruthy = mkAnd(
        mkHeapRefEq(ref, mkTsNullValue()).not(),
        mkHeapRefEq(ref, mkUndefinedValue()).not(),
    )

    stringMaxLengths.keys.fold(referenceIsTruthy) { fallback, trackedString ->
        val trackedStringIsNonEmpty = mkEq(stringLength(trackedString), mkBv(0)).not()
        mkIte(
            condition = mkHeapRefEq(ref, trackedString),
            trueBranch = trackedStringIsNonEmpty,
            falseBranch = fallback,
        )
    }
}

internal fun TsState.copyStringRange(
    receiver: UHeapRef,
    from: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
): UConcreteHeapRef = with(ctx) {
    val source = stringCharacters(receiver)
    val (result, destination) = allocateString(length, maxLength = concreteStringMaxLength(receiver))
    memory.memcpy(
        source,
        destination,
        arrayDescriptorOf(STRING_CHARACTER_ARRAY_TYPE),
        bv16Sort,
        fromSrc = from,
        fromDst = mkBv(0),
        length = length,
    )
    result
}

internal fun TsState.concatStrings(
    left: UHeapRef,
    right: UHeapRef,
): UConcreteHeapRef = with(ctx) {
    val leftCharacters = stringCharacters(left)
    val rightCharacters = stringCharacters(right)
    val leftLength = memory.read(mkArrayLengthLValue(leftCharacters, STRING_CHARACTER_ARRAY_TYPE))
    val rightLength = memory.read(mkArrayLengthLValue(rightCharacters, STRING_CHARACTER_ARRAY_TYPE))
    val resultLength = mkBvAddExpr(leftLength, rightLength)
    val resultMaxLength = concreteStringMaxLength(left)?.let { leftMaxLength ->
        concreteStringMaxLength(right)?.let { rightMaxLength ->
            (leftMaxLength.toLong() + rightMaxLength.toLong())
                .takeIf { it <= Int.MAX_VALUE }
                ?.toInt()
        }
    }
    val (result, destination) = allocateString(resultLength, maxLength = resultMaxLength)

    memory.memcpy(
        leftCharacters,
        destination,
        arrayDescriptorOf(STRING_CHARACTER_ARRAY_TYPE),
        bv16Sort,
        fromSrc = mkBv(0),
        fromDst = mkBv(0),
        length = leftLength,
    )
    memory.memcpy(
        rightCharacters,
        destination,
        arrayDescriptorOf(STRING_CHARACTER_ARRAY_TYPE),
        bv16Sort,
        fromSrc = mkBv(0),
        fromDst = leftLength,
        length = rightLength,
    )
    result
}

internal fun TsState.stringFromCodeUnit(codeUnit: UExpr<KFp64Sort>): UConcreteHeapRef = with(ctx) {
    val (result, characters) = allocateString(mkBv(1), maxLength = 1)
    val value = mkFpToBvExpr(
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = codeUnit,
        bvSize = bv16Sort.sizeBits.toInt(),
        isSigned = false,
    ).asExpr(bv16Sort)
    val character = mkArrayIndexLValue(
        sort = bv16Sort,
        ref = characters,
        index = mkBv(0),
        type = STRING_CHARACTER_ARRAY_TYPE,
    )
    memory.write(
        character,
        value,
        guard = trueExpr,
    )
    result
}
