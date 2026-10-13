package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.deleteKnownArrayIndex

internal fun TsExprResolver.tryDeleteArrayIndex(value: EtsArrayAccess): TsExprApproximationResult = with(ctx) {
    val resolved = resolve(value.array) ?: return TsExprApproximationResult.ResolveFailure
    val rawArray = if (resolved.isFakeObject()) {
        scope.assert(resolved.getFakeType(scope).refTypeExpr) ?: return TsExprApproximationResult.ResolveFailure
        resolved.extractRef(scope)
    } else {
        resolved.asExpr(addressSort)
    }
    checkUndefinedOrNullPropertyRead(scope, rawArray, propertyName = "[]")
        ?: return TsExprApproximationResult.ResolveFailure
    val array = resolveHeapRef(scope, rawArray) ?: return TsExprApproximationResult.ResolveFailure
    val storageType = scope.calcOnState { arrayStorageType(array, value.array.type) }
    if (storageType !is EtsArrayType) return TsExprApproximationResult.NoApproximation

    val knownOwnSlots = scope.calcOnState { array in knownArrayPropertyPresence }
    if (!knownOwnSlots) {
        throw UnsupportedOperationException("Array index deletion requires known own-slot provenance")
    }

    val resolvedIndex = resolve(value.index) ?: return TsExprApproximationResult.ResolveFailure
    val numericIndex = extractNumericArrayIndex(scope, resolvedIndex)
    val supportedKey = mkAnd(numericIndex.isNumeric, mkValidArrayIndexProperty(numericIndex.value))
    scope.fork(supportedKey, blockOnFalseState = {
        terminateAsUnsupported(reason = "Deleting a named array property needs prototype semantics")
    }) ?: return TsExprApproximationResult.ResolveFailure

    val index = mkFpToUint32AfterValidation(numericIndex.value, supportedKey).asExpr(sizeSort)
    scope.doWithState { deleteKnownArrayIndex(array, index) }
    TsExprApproximationResult.SuccessfulApproximation(mkTrue())
}
