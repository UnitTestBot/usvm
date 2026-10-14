package org.usvm.util

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState

/** A generated dense array owns every live slot, including slots whose value is undefined. */
fun TsState.markDenseArrayOwnSlots(
    array: UConcreteHeapRef,
    length: UExpr<TsSizeSort>,
    capacity: Int,
) = with(ctx) {
    val presence: Map<UExpr<TsSizeSort>, UBoolExpr> = (0 until capacity).associate { index ->
        val symbolicIndex = mkBv(index)
        symbolicIndex to mkBvSignedLessExpr(symbolicIndex, length)
    }
    initializedArrayIndices += array to presence
    knownArrayPropertyPresence += array
}

/** Presence of own numeric slots in source arrays and generated array inputs. */
internal fun TsState.knownArrayIndexPresence(array: UHeapRef, index: UExpr<TsSizeSort>): UBoolExpr? {
    if (array !in knownArrayPropertyPresence) return null

    return writtenArrayIndexPresence(array, index)
}

internal fun TsState.writtenArrayIndexPresence(array: UHeapRef, index: UExpr<TsSizeSort>): UBoolExpr = with(ctx) {
    val initialized = initializedArrayIndices[array].orEmpty()
    mkOr(initialized.map { (writtenIndex, present) -> mkAnd(mkEq(index, writtenIndex), present) })
}

internal fun TsState.deleteKnownArrayIndex(array: UHeapRef, index: UExpr<TsSizeSort>) = with(ctx) {
    // Deletion can create a hole without changing length or payload storage.
    if (array is UConcreteHeapRef) {
        denseInputArrays.remove(array)
    } else {
        // A symbolic receiver can alias any recorded dense array.
        denseInputArrays.clear()
    }

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

/** Copies known own slots into a fresh array; the caller establishes complete destination provenance. */
internal fun TsState.copyKnownArrayIndexPresence(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    fromSrc: UExpr<TsSizeSort>,
    fromDst: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
) = with(ctx) {
    require(srcRef in knownArrayPropertyPresence) { "Source array has no known own-slot provenance" }

    val fromSrcEnd = mkBvAddExpr(fromSrc, length)
    val copied = initializedArrayIndices[dstRef].orEmpty().toMutableMap()
    for ((index, present) in initializedArrayIndices[srcRef].orEmpty()) {
        val isInCopiedRange = mkAnd(
            mkBvSignedGreaterOrEqualExpr(index, fromSrc),
            mkBvSignedLessExpr(index, fromSrcEnd),
        )
        val destinationIndex = mkBvAddExpr(fromDst, mkBvSubExpr(index, fromSrc))
        val copiedPresence = mkAnd(isInCopiedRange, present)
        copied[destinationIndex] = mkOr(copied[destinationIndex] ?: falseExpr, copiedPresence)
    }
    initializedArrayIndices += dstRef to copied
}
