package org.usvm.machine.expr

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsWellKnownSymbol
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.initializeArray
import org.usvm.isTrue
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.dispatch
import org.usvm.machine.call.intrinsic.TsIteratorModelFamily
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsUnresolvedArrayKind
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.machine.types.iteUnresolvedValue
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.util.initializeArrayKind
import org.usvm.util.markDenseArrayOwnSlots

/** Native iterator projection; entries yield fresh key/value pair arrays. */
enum class TsIteratorKind { KEYS, VALUES, ENTRIES }

/** Cursor refers to insertion history, so deletions, clear, and later additions remain visible. */
internal data class TsCollectionIterator(
    val receiver: UConcreteHeapRef,
    val kind: TsIteratorKind,
    val index: UExpr<TsSizeSort>,
    val exhausted: UBoolExpr,
)

private val collectionIteratorType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "%BuiltinCollectionIterator"))

internal fun TsState.newCollectionIterator(receiver: UConcreteHeapRef, kind: TsIteratorKind): UHeapRef = with(ctx) {
    val iterator = memory.allocConcrete(collectionIteratorType)
    collectionIterators += iterator to TsCollectionIterator(
        receiver = receiver,
        kind = kind,
        index = mkBv(0),
        exhausted = falseExpr,
    )
    iterator
}

internal fun TsExprResolver.tryApproximateCollectionIteratorCall(
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult = with(ctx) {
    val receiver = stmt.instance as? UConcreteHeapRef ?: return TsExprApproximationResult.NoApproximation
    val iterator = scope.calcOnState { collectionIterators[receiver] }
    val collection = scope.calcOnState { modeledCollections[receiver] }
    if (stmt.call.wellKnownSymbol == EtsWellKnownSymbol.ITERATOR) {
        if (iterator != null) return TsExprApproximationResult.from(receiver)
        if (collection == null) return TsExprApproximationResult.NoApproximation
        val selectedModels = TsBuiltInUnknownCallModels.catalog(options.unknownCallModelSelection).modelIds
        if (TsIteratorModelFamily.COLLECTION_ITERATOR_ID !in selectedModels) {
            return TsExprApproximationResult.NoApproximation
        }
        unknownCallDispatcher.dispatch(
            scope = scope,
            call = stmt.call,
            callSite = stmt.returnSite,
            callee = stmt.call.callee.copy(name = TsIteratorModelFamily.COLLECTION_ITERATOR_METHOD),
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
            resolvedReceiver = receiver,
            resolvedArguments = stmt.args,
        )
        return TsExprApproximationResult.ResolveFailure
    }
    if (iterator == null || stmt.call.callee.name != "next") return TsExprApproximationResult.NoApproximation

    TsExprApproximationResult.from(advanceCollectionIterator(receiver, iterator))
}

private fun TsExprResolver.advanceCollectionIterator(
    reference: UConcreteHeapRef,
    iterator: TsCollectionIterator,
): UHeapRef? = with(ctx) {
    val collection = scope.calcOnState { modeledCollections[iterator.receiver] }
    if (collection == null) {
        throw UnsupportedOperationException("Collection iterator backend was invalidated by a residual mutation")
    }
    var found: UBoolExpr = falseExpr
    var nextIndex = iterator.index
    var value = scope.calcOnState { collectionUndefined() }
    var selectedKey = value
    var selectedValue = value
    // Reverse construction selects the earliest live insertion at or after the cursor.
    for ((index, key) in collection.insertionHistory.withIndex().reversed()) {
        val present = scope.calcOnState { collectionPresent(collection, key) }
        val inRange = mkBvSignedLessOrEqualExpr(iterator.index, mkBv(index))
        val selected = mkAnd(mkNot(iterator.exhausted), inRange, present)
        val keyValue = scope.calcOnState { readCollectionPayload(key) }
        val itemValue = scope.calcOnState { collectionGet(collection, key) }
        selectedKey = iteUnresolvedValue(selected, keyValue, selectedKey)
        selectedValue = iteUnresolvedValue(selected, itemValue, selectedValue)
        nextIndex = mkIte(selected, mkBv(index + 1), nextIndex)
        found = mkOr(found, selected)
    }
    val done = mkNot(found)
    value = when (iterator.kind) {
        TsIteratorKind.KEYS -> selectedKey
        TsIteratorKind.VALUES -> selectedValue
        TsIteratorKind.ENTRIES -> {
            if (done.isTrue) value else scope.calcOnState { collectionValue(iteratorPair(selectedKey, selectedValue)) }
        }
    }
    val resultValue = iteUnresolvedValue(done, scope.calcOnState { collectionUndefined() }, value)
    val materialized = scope.calcOnState { mkFakeValue(scope = scope, value = resultValue) } ?: return null

    scope.calcOnState {
        collectionIterators += reference to iterator.copy(index = nextIndex, exhausted = done)
        val result = memory.allocConcrete(iteratorResultType)
        iteratorResults += result to TsIteratorResult(done = done, value = materialized)
        result
    }
}

/** A fresh two-slot native array preserves heterogeneous entry payloads and their kind selectors. */
internal fun TsState.iteratorPair(first: TsUnresolvedValue, second: TsUnresolvedValue): UConcreteHeapRef = with(ctx) {
    val values = listOf(first, second)
    val type = EtsArrayType(EtsUnknownType, dimensions = 1)
    val pair = memory.allocConcrete(type)
    memory.initializeArray(
        arrayHeapRef = pair,
        type = EtsArrayType(EtsBooleanType, dimensions = 1),
        sort = boolSort,
        sizeSort = sizeSort,
        contents = values.asSequence().map { it.boolValue },
    )
    memory.initializeArray(
        arrayHeapRef = pair,
        type = EtsArrayType(EtsNumberType, dimensions = 1),
        sort = fp64Sort,
        sizeSort = sizeSort,
        contents = values.asSequence().map { it.fpValue },
    )
    memory.initializeArray(
        arrayHeapRef = pair,
        type = type,
        sort = addressSort,
        sizeSort = sizeSort,
        contents = values.asSequence().map { it.refValue },
    )
    initializeArrayKind(pair, TsUnresolvedArrayKind.BOOLEAN, values.map { it.type.boolTypeExpr })
    initializeArrayKind(pair, TsUnresolvedArrayKind.NUMBER, values.map { it.type.fpTypeExpr })
    markDenseArrayOwnSlots(array = pair, length = mkBv(2), capacity = 2)
    pair
}
