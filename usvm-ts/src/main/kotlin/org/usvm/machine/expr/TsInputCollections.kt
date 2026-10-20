package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsWellKnownSymbol
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USymbolicHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.api.typeStreamOf
import org.usvm.isTrue
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModelDispatcher
import org.usvm.machine.call.dispatch
import org.usvm.machine.state.TsState
import org.usvm.machine.state.symbolicStringBackingConstraint
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.sizeSort
import org.usvm.types.singleOrNull

internal fun EtsType.nativeCollectionKind(): TsCollectionKind? {
    val name = when (this) {
        is EtsClassType -> {
            if (signature.file != EtsClassSignature.UNKNOWN.file) return null
            signature.name
        }
        is EtsUnclearRefType -> name
        else -> return null
    }
    return when (name) {
        "Map" -> TsCollectionKind.MAP
        "Set" -> TsCollectionKind.SET
        else -> null
    }
}

/**
 * Finite input domain, including every cardinality from zero to the configured capacity.
 * Symbolic receivers are partitioned by heap equality before a state-owned native backend is selected.
 * The allocated handle is private: set/add return the original receiver and object identity is unchanged.
 */
internal fun TsExprResolver.inputCollectionReference(
    reference: UHeapRef,
    declaredType: EtsType,
): TsIntrinsicFieldResult<UConcreteHeapRef> = with(ctx) {
    val capacity = options.maxInputCollectionSize ?: return TsIntrinsicFieldResult.NotApplicable
    if (unknownCallDispatcher !is TsUnknownCallModelDispatcher) return TsIntrinsicFieldResult.NotApplicable
    val selected = TsBuiltInUnknownCallModels.catalog(options.unknownCallModelSelection).modelIds
    if ("ts.collection.has" !in selected) return TsIntrinsicFieldResult.NotApplicable
    if (reference !is USymbolicHeapRef) return TsIntrinsicFieldResult.NotApplicable

    val sourceType = if (declaredType.nativeCollectionKind() != null) {
        declaredType
    } else {
        scope.calcOnState { inputCollectionTypes[reference] ?: memory.typeStreamOf(reference).singleOrNull() }
            ?: return TsIntrinsicFieldResult.NotApplicable
    }
    val elementTypes = when (sourceType) {
        is EtsClassType -> sourceType.typeParameters
        is EtsUnclearRefType -> sourceType.typeParameters
        else -> emptyList()
    }
    val kind = sourceType.nativeCollectionKind() ?: return TsIntrinsicFieldResult.NotApplicable
    val nativeName = if (kind == TsCollectionKind.MAP) "Map" else "Set"
    // A project class named Map/Set must keep ordinary method/field semantics.
    val shadowed = scene.projectClasses.any { it.name == nativeName }
    if (shadowed) return TsIntrinsicFieldResult.NotApplicable

    val nativeType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = nativeName))
    val nativeConstraint = scope.calcOnState { memory.types.evalTypeEquals(reference, nativeType) }
    scope.assert(nativeConstraint) ?: return TsIntrinsicFieldResult.Stopped

    val known = scope.calcOnState { inputCollectionReferences.toList() }
    for ((original, handle) in known) {
        val equal = mkHeapRefEq(original, reference)
        val aliases = scope.calcOnState { models.first().eval(equal).isTrue }
        val condition = if (aliases) equal else mkNot(equal)
        scope.fork(condition) ?: return TsIntrinsicFieldResult.Stopped
        if (aliases) return TsIntrinsicFieldResult.Handled(handle)
    }

    val handle = scope.calcOnState {
        val handle = memory.allocConcrete(nativeType)
        val collection = newModeledCollection(kind)
        modeledCollections += handle to collection
        inputCollectionReferences += reference to handle
        collectionOriginalReceivers += handle to reference
        initializeInputCollection(handle, capacity, elementTypes)
        handle
    }
    // Refresh models after introducing cardinality, payload-kind and distinct-key constraints.
    scope.assert(trueExpr) ?: return TsIntrinsicFieldResult.Stopped
    TsIntrinsicFieldResult.Handled(handle)
}

internal fun TsExprResolver.tryApproximateInputCollectionCall(
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult = with(ctx) {
    if (stmt.instance.sort != addressSort) return TsExprApproximationResult.NoApproximation
    val collection = inputCollectionReference(stmt.instance.asExpr(addressSort), stmt.call.instance.type)
    val handle = when (collection) {
        is TsIntrinsicFieldResult.Handled -> collection.value
        TsIntrinsicFieldResult.Stopped -> return TsExprApproximationResult.ResolveFailure
        TsIntrinsicFieldResult.NotApplicable -> return TsExprApproximationResult.NoApproximation
    }
    val normalized = TsVirtualMethodCallStmt(
        call = stmt.call,
        instance = handle,
        args = stmt.args,
        returnSite = stmt.returnSite,
        originalCall = stmt.originalCall,
        targetCaptured = stmt.targetCaptured,
    )
    if (stmt.call.wellKnownSymbol == EtsWellKnownSymbol.ITERATOR) {
        return tryApproximateCollectionIteratorCall(normalized)
    }

    unknownCallDispatcher.dispatch(
        scope = scope,
        call = normalized,
        failureReason = TsUnknownCallFailureReason.VIRTUAL_METHOD_NOT_FOUND,
        resolvedReceiver = handle,
    )
    TsExprApproximationResult.ResolveFailure
}

/** Keep readonly native size outside ordinary property storage for symbolic receivers too. */
internal fun TsExprResolver.ensureSupportedInputCollectionMutation(
    reference: UHeapRef,
    declaredType: EtsType,
    property: String,
): Boolean {
    if (property != "size") return true
    return when (inputCollectionReference(reference, declaredType)) {
        is TsIntrinsicFieldResult.Handled -> {
            scope.doWithState { terminateAsUnsupported(reason = "Mutating native collection size is not modeled") }
            false
        }
        TsIntrinsicFieldResult.Stopped -> false
        TsIntrinsicFieldResult.NotApplicable -> true
    }
}

private fun TsState.initializeInputCollection(
    handle: UConcreteHeapRef,
    capacity: Int,
    elementTypes: List<EtsType>,
) = with(ctx) {
    val count = makeSymbolicPrimitive(sizeSort)
    pathConstraints += mkBvSignedGreaterOrEqualExpr(count, mkBv(0))
    pathConstraints += mkBvSignedLessOrEqualExpr(count, mkBv(capacity))
    val previousKeys = mutableListOf<TsUnresolvedValue>()
    for (index in 0 until capacity) {
        val present = mkBvSignedLessExpr(mkBv(index), count)
        val key = newInputCollectionValue(elementTypes.getOrNull(0))
        val collection = modeledCollections.getValue(handle)
        for (previous in previousKeys) {
            val equal = requireNotNull(collectionKeyEquals(previous, key))
            pathConstraints += mkImplies(present, mkNot(equal))
        }
        val value = if (collection.kind == TsCollectionKind.SET) {
            key
        } else {
            newInputCollectionValue(elementTypes.getOrNull(1))
        }
        collectionInsert(handle, collection, key, value)
        val updated = modeledCollections.getValue(handle)
        val identity = updated.keys.last()
        // Build a dense symbolic prefix in insertion order; backend size stays coherent with membership.
        guardInitialCollectionEntry(updated, identity, present)
        previousKeys += key
    }
}

private fun TsState.newInputCollectionValue(declaredType: EtsType?): TsUnresolvedValue = with(ctx) {
    if (declaredType == EtsNumberType) return collectionValue(makeSymbolicPrimitive(fp64Sort))
    if (declaredType == EtsBooleanType) return collectionValue(makeSymbolicPrimitive(boolSort))

    val type = EtsFakeType(
        boolTypeExpr = makeSymbolicPrimitive(boolSort),
        fpTypeExpr = makeSymbolicPrimitive(boolSort),
        refTypeExpr = makeSymbolicPrimitive(boolSort),
    )
    pathConstraints += type.mkExactlyOneTypeConstraint(ctx)
    val kindConstraint = when (declaredType) {
        EtsBooleanType -> type.boolTypeExpr
        EtsNumberType -> type.fpTypeExpr
        EtsStringType, is EtsClassType, is EtsUnclearRefType -> type.refTypeExpr
        else -> trueExpr
    }
    pathConstraints += kindConstraint
    val reference = makeSymbolicRefUntyped()
    if (declaredType == EtsStringType) {
        pathConstraints += mkNotNullOrUndefined(reference)
        pathConstraints += memory.types.evalTypeEquals(reference, EtsStringType)
    }
    val isString = memory.types.evalTypeEquals(reference, EtsStringType)
    val isDefined = mkNotNullOrUndefined(reference)
    val string = mkAnd(type.refTypeExpr, isString, isDefined)
    pathConstraints += mkImplies(string, symbolicStringBackingConstraint(reference))
    boundedStringBackingRefs += reference to maxStringLength

    TsUnresolvedValue(
        boolValue = makeSymbolicPrimitive(boolSort),
        fpValue = makeSymbolicPrimitive(fp64Sort),
        refValue = reference,
        type = type,
    )
}
