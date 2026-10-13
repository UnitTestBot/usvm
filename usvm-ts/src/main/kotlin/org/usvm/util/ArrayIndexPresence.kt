package org.usvm.util

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState

/** Presence of own numeric slots in arrays allocated by a supported NewArray expression. */
internal fun TsState.knownArrayIndexPresence(array: UHeapRef, index: UExpr<TsSizeSort>): UBoolExpr? {
    if (array !in knownArrayPropertyPresence) return null

    return writtenArrayIndexPresence(array, index)
}

internal fun TsState.writtenArrayIndexPresence(array: UHeapRef, index: UExpr<TsSizeSort>): UBoolExpr = with(ctx) {
    val initialized = initializedArrayIndices[array].orEmpty()
    mkOr(initialized.map { (writtenIndex, present) -> mkAnd(mkEq(index, writtenIndex), present) })
}

internal fun TsState.deleteKnownArrayIndex(array: UHeapRef, index: UExpr<TsSizeSort>) = with(ctx) {
    val initialized = initializedArrayIndices[array].orEmpty()
    initializedArrayIndices += array to initialized.mapValues { (writtenIndex, present) ->
        mkAnd(present, mkNot(mkEq(index, writtenIndex)))
    }
}

internal fun TsState.truncateKnownArrayIndices(array: UHeapRef, length: UExpr<TsSizeSort>) = with(ctx) {
    val initialized = initializedArrayIndices[array].orEmpty()
    initializedArrayIndices += array to initialized.mapValues { (index, present) ->
        val inRange = mkAnd(mkBvSignedGreaterOrEqualExpr(index, mkBv(0)), mkBvSignedLessExpr(index, length))
        mkAnd(present, inRange)
    }
}

internal fun TsState.moveInitializedArrayIndices(
    array: UHeapRef,
    newIndex: (UExpr<TsSizeSort>) -> UExpr<TsSizeSort>,
) = with(ctx) {
    val moved = linkedMapOf<UExpr<TsSizeSort>, UBoolExpr>()
    for ((index, present) in initializedArrayIndices[array].orEmpty()) {
        val target = newIndex(index)
        moved[target] = mkOr(moved[target] ?: falseExpr, present)
    }
    initializedArrayIndices += array to moved
}
