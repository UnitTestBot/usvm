package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.isTrue
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue

private const val ITERATOR_MARKER = "__usvm_exact_iterator__"
private const val ITERATOR_SOURCE = "__usvm_iterator_source__"
private const val ITERATOR_INDEX = "__usvm_iterator_index__"
private const val ITERATOR_BOOL_ELEMENTS = "__usvm_iterator_bool_elements__"
private const val ITERATOR_NUMBER_ELEMENTS = "__usvm_iterator_number_elements__"
private const val ITERATOR_RESULT_MARKER = "__usvm_exact_iterator_result__"
private const val ITERATOR_RESULT_DONE = "done"
private const val ITERATOR_RESULT_VALUE = "value"

private val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
private val booleanArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
private val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)

internal fun TsExprResolver.tryApproximateExactIteratorCall(
    expr: EtsInstanceCallExpr,
): TsExprApproximationResult? = with(ctx) {
    if (!options.iteratorModel) return null

    if (expr.callee.name == "Symbol.iterator") {
        if (expr.args.isNotEmpty()) return rejectIterator(expr, "iterator-arguments")
        val resolved = resolve(expr.instance)
            ?: return TsExprApproximationResult.ResolveFailure
        val instance = asSourceRefOrNull(resolved)
            ?: return rejectIterator(expr, "non-reference-receiver")

        // Built-in iterator objects return themselves from @@iterator.
        if (instance is UConcreteHeapRef && isMarked(instance, ITERATOR_MARKER)) {
            return TsExprApproximationResult.SuccessfulApproximation(instance)
        }

        val arrayType = expr.instance.type as? EtsArrayType
            ?: return rejectIterator(expr, "receiver-outside-array-subset")
        if (arrayType.elementType !is EtsUnknownType &&
            arrayType.elementType !is EtsBooleanType &&
            arrayType.elementType !is EtsNumberType
        ) {
            return rejectIterator(expr, "array-element-type-outside-exact-subset")
        }
        val iterator = allocateConcreteRef()
        scope.doWithState {
            memory.write(mkFieldLValue(boolSort, iterator, ITERATOR_MARKER), trueExpr, trueExpr)
            memory.write(mkFieldLValue(addressSort, iterator, ITERATOR_SOURCE), instance, trueExpr)
            memory.write(mkFieldLValue(sizeSort, iterator, ITERATOR_INDEX), mkBv(0), trueExpr)
            memory.write(
                mkFieldLValue(boolSort, iterator, ITERATOR_BOOL_ELEMENTS),
                mkBool(arrayType.elementType is EtsBooleanType),
                trueExpr,
            )
            memory.write(
                mkFieldLValue(boolSort, iterator, ITERATOR_NUMBER_ELEMENTS),
                mkBool(arrayType.elementType is EtsNumberType),
                trueExpr,
            )
        }
        return TsExprApproximationResult.SuccessfulApproximation(iterator)
    }

    if (expr.callee.name == "next" || expr.callee.name == "return") {
        val resolved = resolve(expr.instance)
            ?: return TsExprApproximationResult.ResolveFailure
        val iterator = asConcreteRefOrNull(resolved)
            ?: return rejectIterator(expr, "non-reference-iterator")
        if (!isMarked(iterator, ITERATOR_MARKER)) {
            return rejectIterator(expr, "unmaterialized-iterator")
        }
        if (expr.args.isNotEmpty()) return rejectIterator(expr, "iterator-arguments")

        if (expr.callee.name == "return") {
            return TsExprApproximationResult.SuccessfulApproximation(
                createIteratorResult(done = trueExpr, value = mkUndefinedValue()),
            )
        }

        val source = scope.calcOnState {
            memory.read(mkFieldLValue(addressSort, iterator, ITERATOR_SOURCE))
        }
        val index = scope.calcOnState {
            memory.read(mkFieldLValue(sizeSort, iterator, ITERATOR_INDEX))
        }
        val arrayType = iteratorArrayType(iterator)
        val length = scope.calcOnState { memory.read(mkArrayLengthLValue(source, arrayType)) }
        val done = mkBvSignedGreaterOrEqualExpr(index, length)
        val element = readIteratorElement(iterator, source, index)
        val value = iteWriteIntoFakeObject(scope, done, mkUndefinedValue(), element)
        scope.doWithState {
            memory.write(
                mkFieldLValue(sizeSort, iterator, ITERATOR_INDEX),
                mkBvAddExpr(index, mkBv(1)),
                mkNot(done),
            )
        }
        return TsExprApproximationResult.SuccessfulApproximation(createIteratorResult(done, value))
    }

    null
}

private fun TsExprResolver.iteratorArrayType(iterator: UConcreteHeapRef): EtsArrayType = with(ctx) {
    val isBool = scope.calcOnState {
        memory.read(mkFieldLValue(boolSort, iterator, ITERATOR_BOOL_ELEMENTS)).isTrue
    }
    if (isBool) return booleanArrayType

    val isNumber = scope.calcOnState {
        memory.read(mkFieldLValue(boolSort, iterator, ITERATOR_NUMBER_ELEMENTS)).isTrue
    }
    if (isNumber) numberArrayType else unknownArrayType
}

internal fun TsExprResolver.tryReadExactIteratorResult(
    field: EtsInstanceFieldRef,
): ExactSymbolicResolution = with(ctx) {
    if (!options.iteratorModel || field.field.name !in setOf(ITERATOR_RESULT_DONE, ITERATOR_RESULT_VALUE)) {
        return ExactSymbolicResolution.NotApplicable
    }
    val resolved = resolve(field.instance)
        ?: return ExactSymbolicResolution.PendingOrRejected
    val result = asConcreteRefOrNull(resolved)
        ?: return ExactSymbolicResolution.NotApplicable
    if (!isMarked(result, ITERATOR_RESULT_MARKER)) return ExactSymbolicResolution.NotApplicable

    val value: UExpr<out USort> = scope.calcOnState {
        when (field.field.name) {
            ITERATOR_RESULT_DONE -> memory.read(mkFieldLValue(boolSort, result, ITERATOR_RESULT_DONE))
            ITERATOR_RESULT_VALUE -> memory.read(mkFieldLValue(addressSort, result, ITERATOR_RESULT_VALUE))
            else -> error("unreachable")
        }
    }
    ExactSymbolicResolution.Resolved(value)
}

private fun TsExprResolver.readIteratorElement(
    iterator: UConcreteHeapRef,
    source: UHeapRef,
    index: UExpr<org.usvm.machine.TsSizeSort>,
): UExpr<*> = with(ctx) {
    val isBool = scope.calcOnState {
        memory.read(mkFieldLValue(boolSort, iterator, ITERATOR_BOOL_ELEMENTS)).isTrue
    }
    if (isBool) {
        val value = scope.calcOnState {
            memory.read(mkArrayIndexLValue(boolSort, source, index, booleanArrayType))
        }
        return value.toFakeObject(scope)
    }

    val isNumber = scope.calcOnState {
        memory.read(mkFieldLValue(boolSort, iterator, ITERATOR_NUMBER_ELEMENTS)).isTrue
    }
    if (isNumber) {
        val value = scope.calcOnState {
            memory.read(mkArrayIndexLValue(fp64Sort, source, index, numberArrayType))
        }
        return value.toFakeObject(scope)
    }

    return scope.calcOnState {
        val refLValue = mkArrayIndexLValue(addressSort, source, index, unknownArrayType)
        val ref = memory.read(refLValue)
        if (ref.isFakeObject()) return@calcOnState ref

        val bool = memory.read(mkArrayIndexLValue(boolSort, source, index, booleanArrayType))
        val fp = memory.read(mkArrayIndexLValue(fp64Sort, source, index, numberArrayType))
        val fake = mkFakeValue(scope, bool, fp, ref)
        lValuesToAllocatedFakeObjects += refLValue to fake
        memory.write(refLValue, fake, guard = trueExpr)
        fake
    }
}

private fun TsExprResolver.createIteratorResult(
    done: UExpr<org.usvm.UBoolSort>,
    value: UExpr<*>,
): UConcreteHeapRef = with(ctx) {
    val result = allocateConcreteRef()
    val fakeValue = value.toFakeObject(scope)
    scope.doWithState {
        memory.write(mkFieldLValue(boolSort, result, ITERATOR_RESULT_MARKER), trueExpr, trueExpr)
        memory.write(mkFieldLValue(boolSort, result, ITERATOR_RESULT_DONE), done, trueExpr)
        memory.write(mkFieldLValue(addressSort, result, ITERATOR_RESULT_VALUE), fakeValue, trueExpr)
    }
    result
}

private fun TsExprResolver.isMarked(ref: UConcreteHeapRef, field: String): Boolean = with(ctx) {
    scope.calcOnState { memory.read(mkFieldLValue(boolSort, ref, field)).isTrue }
}

private fun TsExprResolver.rejectIterator(
    expr: EtsInstanceCallExpr,
    detail: String,
): TsExprApproximationResult.ResolveFailure {
    recordSymbolicSemanticFallback(
        SymbolicSemanticReason.ITERATOR_PROTOCOL_OUTSIDE_EXACT_SUBSET,
        "${expr.callee}:$detail",
    )
    scope.assert(ctx.falseExpr)
    return TsExprApproximationResult.ResolveFailure
}

private fun TsExprResolver.asSourceRefOrNull(expr: UExpr<*>): UHeapRef? = with(ctx) {
    when {
        expr.isFakeObject() -> {
            scope.assert(expr.getFakeType(scope).refTypeExpr) ?: return null
            expr.extractRef(scope)
        }

        expr.sort == addressSort -> expr.asExpr(addressSort)
        else -> null
    }
}

private fun TsExprResolver.asConcreteRefOrNull(expr: UExpr<*>): UConcreteHeapRef? {
    val ref = asSourceRefOrNull(expr) ?: return null
    return ref as? UConcreteHeapRef
}
