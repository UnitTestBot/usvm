package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsWellKnownSymbol
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isTrue
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.util.arrayStorageType
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.writtenArrayIndexPresence

/** State belongs to a particular native array iterator, independently of its source binding. */
data class TsArrayIterator(
    val array: UHeapRef,
    val arrayType: EtsArrayType,
    val index: UExpr<TsSizeSort>,
    val exhausted: UBoolExpr,
)

data class TsIteratorResult(val done: UBoolExpr, val value: UExpr<*>)

private val iteratorType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "%BuiltinArrayIterator"))
internal val iteratorResultType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "%BuiltinIteratorResult"))

internal fun TsExprResolver.tryApproximateBuiltinIteratorCall(
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult = with(ctx) {
    if (stmt.call.wellKnownSymbol == EtsWellKnownSymbol.ITERATOR) {
        val string = tryCreateStringIterator(stmt)
        if (string !is TsExprApproximationResult.NoApproximation) return string

        return TsExprApproximationResult.from(createArrayIterator(stmt))
    }

    val reference = stmt.instance as? UConcreteHeapRef ?: return TsExprApproximationResult.NoApproximation
    if (stmt.call.callee.name != "next") return TsExprApproximationResult.NoApproximation

    val stringIterator = scope.calcOnState { stringIterators[reference] }
    if (stringIterator != null) return TsExprApproximationResult.from(advanceStringIterator(reference, stringIterator))

    val arrayIterator = scope.calcOnState { arrayIterators[reference] }
        ?: return TsExprApproximationResult.NoApproximation
    TsExprApproximationResult.from(advanceArrayIterator(reference, arrayIterator))
}

private fun TsExprResolver.createArrayIterator(stmt: TsVirtualMethodCallStmt): UHeapRef? = with(ctx) {
    if (stmt.instance.sort != addressSort) {
        unsupportedArrayIterator("The intrinsic iterator requires a modeled runtime array")
        return null
    }

    val array = stmt.instance.asExpr(addressSort)
    val arrayType = scope.calcOnState { arrayStorageType(array, stmt.call.instance.type) } as? EtsArrayType
    if (arrayType == null) {
        unsupportedArrayIterator("Custom and non-array iterators are not modeled")
        return null
    }

    val runtimeArray = scope.calcOnState { memory.types.evalIsSubtype(array, arrayType) }
    if (scope.checkSat(mkNot(runtimeArray)) != null) {
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_ITERATOR_UNSUPPORTED,
            detail = "The receiver can have a custom or non-array iterator",
        )
    }
    scope.assert(runtimeArray) ?: return null

    scope.calcOnState {
        val iterator = memory.allocConcrete(iteratorType)
        arrayIterators += iterator to TsArrayIterator(
            array = array,
            arrayType = arrayType,
            index = mkBv(0),
            exhausted = falseExpr,
        )
        iterator
    }
}

private fun TsExprResolver.advanceArrayIterator(
    reference: UConcreteHeapRef,
    iterator: TsArrayIterator,
): UHeapRef? = with(ctx) {
    val invalidated = scope.calcOnState { iterator.array in unsupportedIteratorArrays }
    if (invalidated) {
        unsupportedArrayIterator("Array iteration needs known element-presence and value storage")
        return null
    }

    val length = scope.calcOnState { memory.read(mkArrayLengthLValue(iterator.array, iterator.arrayType)) }
    val withinCapacity = mkAnd(
        mkBvSignedGreaterOrEqualExpr(length, mkBv(0)),
        mkBvSignedLessOrEqualExpr(length, mkBv(options.maxArraySize)),
    )
    if (scope.checkSat(mkAnd(mkNot(iterator.exhausted), mkNot(withinCapacity))) != null) {
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_ITERATOR_UNSUPPORTED,
            detail = "The array iterator exceeds the configured length bound ${options.maxArraySize}",
        )
    }
    scope.assert(mkOr(iterator.exhausted, withinCapacity)) ?: return null

    val done = mkOr(iterator.exhausted, mkBvSignedGreaterOrEqualExpr(iterator.index, length))
    val initialized = scope.calcOnState {
        // Inputs are dense; known allocations distinguish written own slots from holes.
        val array = iterator.array
        val denseInput = !isAllocatedConcreteHeapRef(array) || denseInputArrays[array] != null
        val knownOwnSlots = array in knownArrayPropertyPresence
        if (denseInput || knownOwnSlots) {
            trueExpr
        } else {
            writtenArrayIndexPresence(array, iterator.index)
        }
    }
    val supportedRead = mkOr(done, initialized)
    if (scope.checkSat(mkNot(supportedRead)) != null) {
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_ITERATOR_UNSUPPORTED,
            detail = "Array iteration requires known own-slot provenance",
        )
    }
    scope.assert(supportedRead) ?: return null

    val value = if (done.isTrue) {
        mkUndefinedValue()
    } else {
        val element = readArrayElement(scope, iterator.array, iterator.index, iterator.arrayType)
        iteWriteIntoFakeObject(
            scope = scope,
            condition = done,
            trueBranchValue = mkUndefinedValue(),
            falseBranchValue = element,
        )
    }

    scope.calcOnState {
        arrayIterators += reference to iterator.copy(
            index = mkIte(done, iterator.index, mkBvAddExpr(iterator.index, mkBv(1))),
            exhausted = done,
        )
        val result = memory.allocConcrete(iteratorResultType)
        iteratorResults += result to TsIteratorResult(done = done, value = value)
        result
    }
}

internal fun TsExprResolver.readBuiltinIteratorField(reference: UHeapRef, name: String): UExpr<*>? = with(ctx) {
    if (reference !is UConcreteHeapRef) return null

    val result = scope.calcOnState { iteratorResults[reference] }
    if (result != null) {
        return when (name) {
            "done" -> result.done
            "value" -> result.value
            else -> mkUndefinedValue()
        }
    }

    val isIterator = scope.calcOnState { arrayIterators[reference] != null || stringIterators[reference] != null }
    if (isIterator && name == "return") return mkUndefinedValue()

    null
}

private fun TsExprResolver.unsupportedArrayIterator(detail: String) {
    reportRuntimeFeatureLimitation(TsRuntimeFeatureLimitationReason.ARRAY_ITERATOR_UNSUPPORTED, detail)
    scope.assert(ctx.falseExpr)
}
