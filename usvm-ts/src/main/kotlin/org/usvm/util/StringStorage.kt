package org.usvm.util

import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.initializeArrayLength
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState
import org.usvm.sizeSort

private val STRING_CHARACTER_ARRAY_TYPE = EtsArrayType(EtsNumberType, dimensions = 1)

internal fun TsState.stringCharacters(receiver: UHeapRef): UHeapRef = with(ctx) {
    memory.read(mkStringBackingLValue(receiver)).asExpr(addressSort)
}

internal fun TsState.stringLength(receiver: UHeapRef): UExpr<TsSizeSort> {
    val characters = stringCharacters(receiver)
    return memory.read(mkStringBackingLengthLValue(characters))
}

private fun TsState.allocateString(
    length: UExpr<TsSizeSort>,
    maxLength: Int,
): Pair<UConcreteHeapRef, UConcreteHeapRef> = with(ctx) {
    val string = memory.allocConcrete(EtsStringType)
    val characters = memory.allocConcrete(STRING_CHARACTER_ARRAY_TYPE.elementType)
    memory.initializeArrayLength(
        arrayHeapRef = characters,
        type = stringBackingArrayDescriptor,
        sizeSort = sizeSort,
        count = length,
    )
    memory.write(
        mkStringBackingLValue(string),
        characters,
        guard = trueExpr,
    )
    boundedStringBackingRefs += string to maxLength

    string to characters
}

internal fun TsState.stringFromCodeUnit(codeUnit: UExpr<KFp64Sort>): UConcreteHeapRef = with(ctx) {
    val value = mkFpToBvExpr(
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = codeUnit,
        bvSize = bv16Sort.sizeBits.toInt(),
        isSigned = false,
    ).asExpr(bv16Sort)

    stringFromUtf16CodeUnit(value)
}

internal fun TsState.stringFromUtf16CodeUnit(codeUnit: UExpr<KBv16Sort>): UConcreteHeapRef = with(ctx) {
    val (result, characters) = allocateString(length = mkBv(1), maxLength = 1)
    val character = mkStringBackingElementLValue(ref = characters, index = mkBv(0))
    memory.write(
        character,
        codeUnit,
        guard = trueExpr,
    )
    result
}

internal fun TsState.writeStringCodeUnits(
    characters: UHeapRef,
    length: UExpr<TsSizeSort>,
    codeUnits: List<UExpr<KBv16Sort>>,
): Unit = with(ctx) {
    codeUnits.forEachIndexed { index, codeUnit ->
        val position = mkBv(index)
        val liveIndex = mkBvSignedLessExpr(position, length)
        val element = mkStringBackingElementLValue(ref = characters, index = position)

        memory.write(element, codeUnit, guard = liveIndex)
    }
}

/** Initialize the private UTF-16 backing of one bounded symbolic string input. */
fun TsState.allocateBoundedString(
    length: UExpr<TsSizeSort>,
    codeUnits: List<UExpr<KBv16Sort>>,
): UConcreteHeapRef = with(ctx) {
    val maxLength = codeUnits.size
    val nonnegativeLength = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
    val boundedLength = mkBvSignedLessOrEqualExpr(length, mkBv(maxLength))
    pathConstraints += mkAnd(nonnegativeLength, boundedLength)
    val (string, characters) = allocateString(length = length, maxLength = maxLength)
    writeStringCodeUnits(characters = characters, length = length, codeUnits = codeUnits)

    string
}
