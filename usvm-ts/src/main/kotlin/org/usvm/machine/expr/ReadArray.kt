package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.readUnresolvedArrayElement
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.readTypedArrayElement

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleArrayAccess(
    value: EtsArrayAccess,
): UExpr<*>? = with(ctx) {
    // Resolve the array.
    val rawArray = run {
        val resolved = resolve(value.array) ?: return null
        if (resolved.isFakeObject()) {
            scope.assert(resolved.getFakeType(scope).refTypeExpr) ?: run {
                logger.warn { "UNSAT after ensuring fake object is ref-typed" }
                return null
            }
            resolved.extractRef(scope)
        } else {
            check(resolved.sort == addressSort) {
                "Expected address sort for array, got: ${resolved.sort}"
            }
            resolved.asExpr(addressSort)
        }
    }

    // Check for undefined or null array access.
    checkUndefinedOrNullPropertyRead(scope, rawArray, propertyName = "[]") ?: return null
    val array = resolveHeapRef(scope, rawArray) ?: return null

    // Resolve the index.
    val resolvedIndex = resolve(value.index) ?: return null
    val storageType = scope.calcOnState { arrayStorageType(array, value.array.type) }
    if (storageType == EtsStringType) {
        return readStringIndex(receiver = array, index = resolvedIndex)
    }

    val propertyName = concreteStringValue(resolvedIndex)
    if (propertyName != null && storageType !is EtsArrayType) {
        val field = EtsFieldSignature(
            name = propertyName,
            enclosingClass = EtsClassSignature.UNKNOWN,
            type = EtsUnknownType,
        )
        return resolveField(scope, value.array, array, field, hierarchy)
    }

    if (storageType !is EtsArrayType) {
        stopRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_STORAGE_TYPE,
            detail = "Indexed access requires supported storage: static=${value.array.type}, storage=$storageType",
        )
        return null
    }

    val index = extractNumericArrayIndex(scope, resolvedIndex)
    val unsupportedKeyIsFeasible = scope.checkSat(index.hasUnsupportedReadKey) != null
    if (unsupportedKeyIsFeasible) {
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_READ,
            detail = "property key requires unsupported ToPropertyKey conversion: $resolvedIndex",
        )
    }
    scope.assert(mkNot(index.hasUnsupportedReadKey)) ?: return null

    check(storageType is EtsArrayType) {
        "Expected EtsArrayType, got: ${value.array.type}"
    }

    val indexIsSupported = mkAnd(
        index.isNumeric,
        mkValidArrayIndexProperty(
            value = index.value,
        ),
    )
    val bvIndex = mkFpToUint32AfterValidation(index.value, indexIsSupported).asExpr(sizeSort)

    readArrayProperty(scope, array, bvIndex, indexIsSupported, storageType)
}

private fun TsContext.readArrayProperty(
    scope: TsStepScope,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    indexIsSupported: UBoolExpr,
    arrayType: EtsArrayType,
): UExpr<*>? {
    checkNotFake(array)

    // Read the array length.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    val elementExists = mkAnd(
        indexIsSupported,
        mkBvSignedGreaterOrEqualExpr(index, mkBv(0)),
        mkBvSignedLessExpr(index, length),
    )
    if (elementExists.isFalse) {
        return mkUndefinedValue()
    }

    val element = readArrayElement(scope, array, index, arrayType)
    if (elementExists.isTrue) {
        return element
    }

    return iteWriteIntoFakeObject(
        scope = scope,
        condition = elementExists,
        trueBranchValue = element,
        falseBranchValue = mkUndefinedValue(),
    )
}

private fun TsContext.readArrayElement(
    scope: TsStepScope,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    arrayType: EtsArrayType,
): UExpr<*> {
    // Determine the element sort.
    val sort = typeToSort(arrayType.elementType)

    // If the element type is known, we can read it directly.
    if (sort !is TsUnresolvedSort) {
        return scope.calcOnState {
            val element = readTypedArrayElement(array, index, arrayType)
            if (element.hasBoxedValue.isFalse) {
                element.nativeValue
            } else {
                mkFakeValue(scope = scope, value = element.unresolvedValue)
            }
        }
    }

    return scope.calcOnState {
        val value = readUnresolvedArrayElement(memory, array, index)
        val fakeObj = mkFakeValue(scope = scope, value = value)
        if (fakeObj != value.refValue) {
            val refLValue = mkArrayIndexLValue(addressSort, array, index, arrayType)
            memory.write(refLValue, fakeObj, guard = trueExpr)
        }

        fakeObj
    }
}
