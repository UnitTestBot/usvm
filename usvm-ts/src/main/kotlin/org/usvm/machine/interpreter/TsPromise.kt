package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.objectLiteralClass
import org.usvm.machine.expr.ensureNoPrototypeMutation
import org.usvm.machine.state.TsState
import org.usvm.types.singleOrNull
import org.usvm.util.EtsHierarchy
import org.usvm.util.getAllFields
import org.usvm.util.getAllMethods
import org.usvm.util.mkFieldLValue

enum class PromiseState {
    PENDING,
    FULFILLED,
    REJECTED;
}

data class PromiseCallback(
    val promise: UConcreteHeapRef,
    val settlement: PromiseState,
) {
    init {
        require(settlement != PromiseState.PENDING)
    }
}

/** A conservative proof for values whose `then` lookup cannot start promise assimilation. */
internal fun TsContext.isDefinitelyNonThenable(
    scope: TsStepScope,
    value: UHeapRef,
    hierarchy: EtsHierarchy,
): Boolean {
    if (!isAllocatedConcreteHeapRef(value) || value.isFakeObject()) return false

    val runtimeType = scope.calcOnState { memory.typeStreamOf(value).singleOrNull() }
    if (runtimeType == EtsStringType) return true

    val objectClass = objectLiteralClass(scope, value, hierarchy) ?: return false
    val thenWasWritten = scope.calcOnState {
        (value to "then") in writtenConcreteFields ||
            trackedObjectProperties.any { it.instance == value && it.name == "then" }
    }
    val hasDeclaredThen = objectClass.getAllFields(hierarchy).any { it.name == "then" } ||
        objectClass.getAllMethods(hierarchy).any { it.name == "then" }
    if (thenWasWritten || hasDeclaredThen) return false

    scope.ensureNoPrototypeMutation(value, objectClass)
    return true
}

/** Materializes one settled, state-owned Promise in the same format as Promise.resolve/reject. */
internal fun TsContext.createSettledPromise(
    scope: TsStepScope,
    value: UExpr<*>,
    settlement: PromiseState,
): UConcreteHeapRef {
    require(settlement != PromiseState.PENDING)

    val promise = allocateConcreteRef()
    val storedValue = value.toFakeObject(scope)
    scope.doWithState {
        markResolved(promise)
        setPromiseState(promise, settlement)
        setResolvedValue(promise, storedValue)
    }
    return promise
}

/** Returns a modeled Promise to reuse, or null when a value is safe to fulfill without assimilation. */
internal fun TsContext.existingPromiseOrEnsureNonThenable(
    scope: TsStepScope,
    value: UExpr<*>,
    hierarchy: EtsHierarchy,
    operation: String,
): UConcreteHeapRef? {
    val actualValue = if (value.isFakeObject()) {
        value.extractSingleValueFromFakeObjectOrNull(scope)
            ?: throw UnsupportedOperationException("$operation has a symbolic runtime kind")
    } else {
        value
    }
    if (actualValue.sort != addressSort) return null
    if (actualValue == mkTsNullValue() || actualValue == mkUndefinedValue()) return null

    val reference = actualValue.asExpr(addressSort)
    if (!isAllocatedConcreteHeapRef(reference)) {
        throw UnsupportedOperationException("$operation requires unmodeled thenable assimilation")
    }

    val registeredPromise = scope.calcOnState {
        promiseState[reference] != null || promiseExecutor[reference] != null
    }
    if (registeredPromise) {
        return reference
    }
    if (!isDefinitelyNonThenable(scope, reference, hierarchy)) {
        throw UnsupportedOperationException("$operation may require unmodeled thenable assimilation")
    }

    return null
}

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
