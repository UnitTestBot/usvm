package org.usvm.util

import io.ksmt.expr.KBitVec16Value
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.initializeArray
import org.usvm.api.initializeArrayLength
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.extractInt
import org.usvm.machine.state.TsState
import org.usvm.model.UModelBase
import org.usvm.sizeSort

/** Creates a string with arbitrary UTF-16 contents within the declared inclusive length bounds. */
fun TsState.mkSymbolicString(minLength: Int, maxLength: Int): UConcreteHeapRef = with(ctx) {
    require(minLength >= 0 && maxLength >= minLength) { "Invalid symbolic string length bounds" }

    val string = memory.allocConcrete(EtsStringType)
    val charactersType = EtsArrayType(EtsNumberType, dimensions = 1)
    val characters = memory.allocConcrete(charactersType)
    val length = makeSymbolicPrimitive(sizeSort)
    val meetsMinimum = mkBvSignedGreaterOrEqualExpr(length, mkBv(minLength))
    val meetsMaximum = mkBvSignedLessOrEqualExpr(length, mkBv(maxLength))
    val contents = sequence {
        repeat(maxLength) {
            yield(makeSymbolicPrimitive(bv16Sort))
        }
    }

    memory.initializeArray(
        arrayHeapRef = characters,
        type = stringBackingArrayDescriptor,
        sort = bv16Sort,
        sizeSort = sizeSort,
        contents = contents,
    )
    memory.initializeArrayLength(characters, stringBackingArrayDescriptor, sizeSort, length)
    memory.write(mkStringBackingLValue(string), characters, guard = trueExpr)
    pathConstraints += mkAnd(meetsMinimum, meetsMaximum)
    boundedStringBackingRefs += string to maxLength

    string
}

/** Reads the same private length region used by ordinary TypeScript string operations. */
fun TsState.readStringLength(string: UHeapRef): UExpr<TsSizeSort> {
    val characters = memory.read(mkStringBackingLValue(string))
    return memory.read(mkStringBackingLengthLValue(characters))
}

/** Resolves a solver-selected witness from this state's memory without changing its input snapshot. */
fun TsState.resolveStringValue(string: UHeapRef, model: UModelBase<EtsType>, maxLength: Int): String = with(ctx) {
    val concrete = model.eval(string) as? UConcreteHeapRef
        ?: error("String reference did not resolve to a concrete heap reference")
    val constant = getStringConstantValue(concrete)
    if (constant != null) {
        return@with constant
    }

    val characters = model.eval(memory.read(mkStringBackingLValue(string)))
    val length = model.eval(memory.read(mkStringBackingLengthLValue(characters))).extractInt()
    require(length in 0..maxLength) { "Resolved string length $length exceeds its declared domain" }

    buildString(length) {
        repeat(length) { index ->
            val lValue = mkStringBackingElementLValue(characters, mkBv(index))
            val character = model.eval(memory.read(lValue)) as KBitVec16Value
            append(character.shortValue.toInt().toChar())
        }
    }
}
