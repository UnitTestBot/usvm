package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapRemove
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.evalTypeEquals
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.machine.types.extractValue
import org.usvm.sizeSort
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue

internal const val MAX_MODELED_COLLECTION_ENTRIES = 16

internal enum class TsCollectionKind { MAP, SET }

/** Native object identity and symbolic backend are shared by all aliases and cloned with the state. */
internal data class TsModeledCollection(
    val kind: TsCollectionKind,
    val storage: UConcreteHeapRef,
    val keys: List<UConcreteHeapRef> = emptyList(),
    val insertionHistory: List<UConcreteHeapRef> = emptyList(),
)

internal data class TsCollectionMatch(val key: UConcreteHeapRef, val guard: UBoolExpr)

private enum class CollectionPayload { BOOLEAN, NUMBER, REFERENCE, IS_BOOLEAN, IS_NUMBER, IS_REFERENCE }
private val storageType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "%CollectionStorage"))
private val payloadType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "%CollectionPayload"))

internal fun TsState.newModeledCollection(kind: TsCollectionKind) = TsModeledCollection(
    kind = kind,
    storage = mkSymbolicObjectMap(mapType = storageType).asExpr(ctx.addressSort) as UConcreteHeapRef,
)

internal fun TsState.collectionValue(value: UExpr<*>): TsUnresolvedValue = with(ctx) {
    val (boolean, isBoolean) = extractValue(value, boolSort, ::getIntermediateBoolLValue)
    val (number, isNumber) = extractValue(value, fp64Sort, ::getIntermediateFpLValue)
    val (reference, isReference) = extractValue(value, addressSort, ::getIntermediateRefLValue)
    TsUnresolvedValue(
        boolValue = boolean ?: falseExpr,
        fpValue = number ?: mkFp64(0.0),
        refValue = reference ?: mkUndefinedValue(),
        type = EtsFakeType(isBoolean, isNumber, isReference),
    )
}

internal fun TsState.collectionUndefined(): TsUnresolvedValue = collectionValue(ctx.mkUndefinedValue())

internal fun TsState.collectionMatches(
    collection: TsModeledCollection,
    value: TsUnresolvedValue,
): List<TsCollectionMatch>? = with(ctx) {
    val matches = mutableListOf<TsCollectionMatch>()
    var unmatched: UBoolExpr = trueExpr
    for (key in collection.keys) {
        val present = symbolicObjectMapContains(collection.storage, key, storageType)
        if (present.isFalse) continue

        val equal = collectionKeyEquals(readCollectionPayload(key), value) ?: return null
        val selected = mkAnd(unmatched, present, equal)
        if (!selected.isFalse) matches += TsCollectionMatch(key = key, guard = selected)
        unmatched = mkAnd(unmatched, mkNot(mkAnd(present, equal)))
    }
    matches
}

internal fun TsState.collectionGet(collection: TsModeledCollection, key: UConcreteHeapRef): TsUnresolvedValue {
    val value = symbolicObjectMapGet(collection.storage, key, storageType, ctx.addressSort)
    return readCollectionPayload(value)
}

internal fun TsState.collectionPut(
    collection: TsModeledCollection,
    key: UConcreteHeapRef,
    value: TsUnresolvedValue,
) {
    val payload = allocateCollectionPayload(value)
    symbolicObjectMapPut(collection.storage, key, payload, storageType, ctx.addressSort)
}

internal fun TsState.collectionInsert(
    receiver: UConcreteHeapRef,
    collection: TsModeledCollection,
    key: TsUnresolvedValue,
    value: TsUnresolvedValue,
) = with(ctx) {
    // SameValueZero stores either sign of numeric zero as positive zero.
    val zero = mkFp64(0.0)
    val normalizedKey = key.copy(fpValue = mkIte(mkFpEqualExpr(key.fpValue, zero), zero, key.fpValue))
    val identity = allocateCollectionPayload(normalizedKey)
    val storedValue = if (collection.kind == TsCollectionKind.SET) normalizedKey else value
    collectionPut(collection, identity, storedValue)
    modeledCollections += receiver to collection.copy(
        keys = collection.keys + identity,
        insertionHistory = collection.insertionHistory + identity,
    )
}

internal fun TsState.collectionRemove(
    receiver: UConcreteHeapRef,
    collection: TsModeledCollection,
    key: UConcreteHeapRef,
) {
    symbolicObjectMapRemove(collection.storage, key, storageType)
    modeledCollections += receiver to collection.copy(keys = collection.keys - key)
}

internal fun TsState.collectionClear(receiver: UConcreteHeapRef, collection: TsModeledCollection) {
    for (key in collection.keys) {
        symbolicObjectMapRemove(collection.storage, key, storageType)
    }
    modeledCollections += receiver to collection.copy(keys = emptyList())
}

/** Initial bounded slots have symbolic presence; cardinality changes under the identical guard. */
internal fun TsState.guardInitialCollectionEntry(
    collection: TsModeledCollection,
    key: UConcreteHeapRef,
    present: UBoolExpr,
) = with(ctx) {
    val membership = URefSetEntryLValue(collection.storage, key, storageType)
    val length = UMapLengthLValue(collection.storage, storageType, sizeSort)
    val currentSize = memory.read(length)
    val absent = mkNot(present)
    memory.write(membership, falseExpr, guard = absent)
    memory.write(length, mkBvSubExpr(currentSize, mkBv(1)), guard = absent)
}

internal fun TsState.collectionPresent(collection: TsModeledCollection, key: UConcreteHeapRef): UBoolExpr =
    symbolicObjectMapContains(collection.storage, key, storageType)

internal fun TsState.collectionSize(collection: TsModeledCollection): UExpr<*> = with(ctx) {
    val size = symbolicObjectMapSize(collection.storage, storageType)
    mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = size.cast(),
        signed = true,
    )
}

/** A residual mutation must not leave later precise model calls observing an obsolete backend. */
internal fun TsState.invalidateCollectionAfterResidual(call: TsUnknownCall) {
    val receiver = call.receiver?.resolved as? UConcreteHeapRef ?: return
    if (receiver !in modeledCollections) return
    val readonlyOperation = call.callee.name in setOf("get", "has", "keys", "values", "entries")
    if (readonlyOperation) return

    modeledCollections -= receiver
}

private fun TsState.allocateCollectionPayload(value: TsUnresolvedValue): UConcreteHeapRef = with(ctx) {
    val ref = memory.allocConcrete(payloadType)
    memory.write(UFieldLValue(boolSort, ref, CollectionPayload.BOOLEAN), value.boolValue, trueExpr)
    memory.write(UFieldLValue(fp64Sort, ref, CollectionPayload.NUMBER), value.fpValue, trueExpr)
    memory.write(UFieldLValue(addressSort, ref, CollectionPayload.REFERENCE), value.refValue, trueExpr)
    memory.write(UFieldLValue(boolSort, ref, CollectionPayload.IS_BOOLEAN), value.type.boolTypeExpr, trueExpr)
    memory.write(UFieldLValue(boolSort, ref, CollectionPayload.IS_NUMBER), value.type.fpTypeExpr, trueExpr)
    memory.write(UFieldLValue(boolSort, ref, CollectionPayload.IS_REFERENCE), value.type.refTypeExpr, trueExpr)
    ref
}

internal fun TsState.readCollectionPayload(ref: UHeapRef): TsUnresolvedValue = with(ctx) {
    val boolean = memory.read(UFieldLValue(boolSort, ref, CollectionPayload.BOOLEAN))
    val number = memory.read(UFieldLValue(fp64Sort, ref, CollectionPayload.NUMBER))
    val reference = memory.read(UFieldLValue(addressSort, ref, CollectionPayload.REFERENCE))
    val isBoolean = memory.read(UFieldLValue(boolSort, ref, CollectionPayload.IS_BOOLEAN))
    val isNumber = memory.read(UFieldLValue(boolSort, ref, CollectionPayload.IS_NUMBER))
    val isReference = memory.read(UFieldLValue(boolSort, ref, CollectionPayload.IS_REFERENCE))
    val type = EtsFakeType(boolTypeExpr = isBoolean, fpTypeExpr = isNumber, refTypeExpr = isReference)

    TsUnresolvedValue(boolValue = boolean, fpValue = number, refValue = reference, type = type)
}

internal fun TsState.collectionKeyEquals(left: TsUnresolvedValue, right: TsUnresolvedValue): UBoolExpr? = with(ctx) {
    val booleanEqual = mkAnd(left.type.boolTypeExpr, right.type.boolTypeExpr, mkEq(left.boolValue, right.boolValue))
    val leftIsNaN = mkFpIsNaNExpr(left.fpValue)
    val rightIsNaN = mkFpIsNaNExpr(right.fpValue)
    val bothNaN = mkAnd(leftIsNaN, rightIsNaN)
    val sameNumber = mkFpEqualExpr(left.fpValue, right.fpValue)
    val sameNumberKey = mkOr(sameNumber, bothNaN)
    val numberEqual = mkAnd(
        left.type.fpTypeExpr,
        right.type.fpTypeExpr,
        sameNumberKey,
    )
    val bothReferences = mkAnd(left.type.refTypeExpr, right.type.refTypeExpr)
    val referenceEqual = if (bothReferences.isFalse) {
        falseExpr
    } else {
        referenceOrStringKeyEquals(left.refValue, right.refValue) ?: return null
    }
    val referencesEqual = mkAnd(bothReferences, referenceEqual)
    mkOr(booleanEqual, numberEqual, referencesEqual)
}

private fun TsState.referenceOrStringKeyEquals(left: UHeapRef, right: UHeapRef): UBoolExpr? = with(ctx) {
    val sameReference = mkHeapRefEq(left, right)
    if (sameReference.isTrue) return trueExpr
    val leftIsString = memory.types.evalTypeEquals(left, EtsStringType)
    val rightIsString = memory.types.evalTypeEquals(right, EtsStringType)
    if (leftIsString.isFalse || rightIsString.isFalse) return sameReference

    val leftConstant = (left as? UConcreteHeapRef)?.let(::getStringConstantValue)
    val rightConstant = (right as? UConcreteHeapRef)?.let(::getStringConstantValue)
    if (leftConstant != null && rightConstant != null) return mkBool(leftConstant == rightConstant)
    val leftBound = modeledStringBound(left) ?: return null
    val rightBound = modeledStringBound(right) ?: return null
    val leftChars = memory.read(mkStringBackingLValue(left))
    val rightChars = memory.read(mkStringBackingLValue(right))
    val leftLength = memory.read(mkStringBackingLengthLValue(leftChars))
    val rightLength = memory.read(mkStringBackingLengthLValue(rightChars))
    val charactersEqual = (0 until maxOf(leftBound, rightBound)).map { index ->
        val position = mkBv(index)
        val leftCharacter = memory.read(mkStringBackingElementLValue(leftChars, position))
        val rightCharacter = memory.read(mkStringBackingElementLValue(rightChars, position))
        mkImplies(mkBvSignedLessExpr(position, leftLength), mkEq(leftCharacter, rightCharacter))
    }
    val stringsEqual = mkAnd(leftIsString, rightIsString, mkEq(leftLength, rightLength), mkAnd(charactersEqual))
    mkOr(sameReference, stringsEqual)
}

private fun TsState.modeledStringBound(ref: UHeapRef): Int? = with(ctx) {
    boundedStringBackingRefs[ref]?.let { return it }
    if (ref is UConcreteHeapRef) {
        val constant = getStringConstantValue(ref)
        if (constant != null) return constant.length
        if (memory.types.evalTypeEquals(ref, EtsStringType).isFalse) return 0
        return null
    }
    if (ref !is UIteExpr<*>) return null
    val trueBound = modeledStringBound(ref.trueBranch.asExpr(addressSort)) ?: return null
    val falseBound = modeledStringBound(ref.falseBranch.asExpr(addressSort)) ?: return null
    maxOf(trueBound, falseBound)
}
