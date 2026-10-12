package org.usvm.util

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.initializeArrayLength
import org.usvm.api.memcpy
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState
import org.usvm.sizeSort

internal val STRING_CHARACTER_ARRAY_TYPE = EtsArrayType(EtsNumberType, dimensions = 1)

internal fun TsState.stringCharacters(receiver: UHeapRef): UHeapRef = with(ctx) {
    memory.read(mkStringBackingLValue(receiver)).asExpr(addressSort)
}

internal fun TsState.stringLength(receiver: UHeapRef): UExpr<TsSizeSort> {
    val characters = stringCharacters(receiver)
    return memory.read(mkStringBackingLengthLValue(characters))
}

fun TsState.markStringMaxLength(
    string: UConcreteHeapRef,
    maxLength: Int,
) {
    require(maxLength >= 0) { "String maximum length must be non-negative" }
    boundedStringBackingRefs += string to maxLength
}

private fun TsState.trackedStringMaxLength(string: UHeapRef): Int? =
    boundedStringBackingRefs[string]

internal fun TsState.allocateString(
    length: UExpr<TsSizeSort>,
    maxLength: Int? = null,
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
    if (maxLength != null) {
        markStringMaxLength(string = string, maxLength = maxLength)
    }

    string to characters
}

internal fun TsState.copyStringRange(
    receiver: UHeapRef,
    from: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
): UConcreteHeapRef = with(ctx) {
    val source = stringCharacters(receiver)
    val (result, destination) = allocateString(length, maxLength = trackedStringMaxLength(receiver))
    memory.memcpy(
        source,
        destination,
        stringBackingArrayDescriptor,
        bv16Sort,
        fromSrc = from,
        fromDst = mkBv(0),
        length = length,
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
    val character = mkStringBackingElementLValue(ref = characters, index = mkBv(0))
    memory.write(
        character,
        value,
        guard = trueExpr,
    )
    result
}
