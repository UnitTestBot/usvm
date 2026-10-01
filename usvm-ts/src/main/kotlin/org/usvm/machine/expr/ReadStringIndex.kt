package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.util.STRING_CHARACTER_ARRAY_TYPE
import org.usvm.util.allocateString
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.stringCharacters
import org.usvm.util.stringLength

/** Indexed string access returns one UTF-16 code unit, or undefined for a missing property. */
internal fun TsContext.readStringIndex(
    scope: TsStepScope,
    string: UHeapRef,
    index: UExpr<KFp64Sort>,
    indexIsNumeric: UBoolExpr,
): UExpr<*> = scope.calcOnState {
    val indexIsValid = mkAnd(
        indexIsNumeric,
        mkValidArrayIndexProperty(index, maximumSupportedIndex = Int.MAX_VALUE),
    )
    val storageIndex = mkFpToUint32AfterValidation(index, indexIsValid).asExpr(sizeSort)
    val length = stringLength(string)
    val indexIsInBounds = mkBvSignedLessExpr(storageIndex, length)
    val propertyExists = mkAnd(indexIsValid, indexIsInBounds)
    val characters = stringCharacters(string)
    val sourceSlot = mkArrayIndexLValue(
        sort = bv16Sort,
        ref = characters,
        index = storageIndex,
        type = STRING_CHARACTER_ARRAY_TYPE,
    )
    val codeUnit = memory.read(sourceSlot)
    val (result, destination) = allocateString(length = mkBv(1), maxLength = 1)
    val destinationSlot = mkArrayIndexLValue(
        sort = bv16Sort,
        ref = destination,
        index = mkBv(0),
        type = STRING_CHARACTER_ARRAY_TYPE,
    )
    memory.write(destinationSlot, codeUnit, guard = trueExpr)

    mkIte(propertyExists, result, mkUndefinedValue())
}
