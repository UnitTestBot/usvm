package org.usvm.machine.interpreter

import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsState
import org.usvm.util.mkFieldLValue

internal const val RESOLVED_FIELD = "__resolved__"

internal fun TsContext.mkResolvedField(ref: UHeapRef): UFieldLValue<String, UBoolSort> {
    return mkFieldLValue(boolSort, ref, RESOLVED_FIELD)
}

// TODO: this should be an extension of UHeapRef or TsContext, not TsState !!!
internal fun TsState.isResolved(promise: UHeapRef): Boolean = with(ctx) {
    val resolvedField = mkResolvedField(promise)
    return memory.read(resolvedField).isTrue
}

internal fun TsState.markResolved(promise: UHeapRef) = with(ctx) {
    val resolvedField = mkResolvedField(promise)
    memory.write(resolvedField, trueExpr, guard = trueExpr)
}

internal const val RESOLVED_VALUE_FIELD = "__value"

internal fun <Sort : USort> TsContext.mkResolvedValueField(
    ref: UHeapRef,
    sort: Sort,
): UFieldLValue<String, Sort> {
    return mkFieldLValue(sort, ref, RESOLVED_VALUE_FIELD)
}

// This should be an extension of TsContext, not TsState !!!
internal fun <Sort : USort> TsState.getResolvedValue(
    promise: UHeapRef,
    sort: Sort,
): UExpr<Sort> = with(ctx) {
    check(sort != unresolvedSort)
    val resolvedValueField = mkResolvedValueField(promise, sort)
    memory.read(resolvedValueField)
}

internal fun <Sort : USort> TsState.setResolvedValue(
    promise: UHeapRef,
    value: UExpr<Sort>,
) = with(ctx) {
    check(value.sort != unresolvedSort)
    val resolvedValueField = mkResolvedValueField(promise, value.sort)
    memory.write(resolvedValueField, value, guard = trueExpr)
}
