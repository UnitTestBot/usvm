package org.usvm.util

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.allocateConcreteRef
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.machine.types.iteUnresolvedValue
import org.usvm.machine.types.mkFakeValue

/** Extra cells preserve values whose runtime kind differs from an allocated array's inferred element type. */
internal enum class TsTypedArrayStorage {
    HAS_BOXED_VALUE,
    BOXED_VALUE,
}

internal fun TsContext.typedArrayOverrideLValue(array: UHeapRef, index: UExpr<TsSizeSort>) =
    UArrayIndexLValue(boolSort, array, index, TsTypedArrayStorage.HAS_BOXED_VALUE)

internal fun TsContext.typedArrayBoxedLValue(array: UHeapRef, index: UExpr<TsSizeSort>) =
    UArrayIndexLValue(addressSort, array, index, TsTypedArrayStorage.BOXED_VALUE)

internal data class TsTypedArrayElement(
    val nativeValue: UExpr<*>,
    val hasBoxedValue: UBoolExpr,
    val unresolvedValue: TsUnresolvedValue,
)

/** Captures both representations before a model shifts or copies the underlying storage. */
internal fun TsState.readTypedArrayElement(
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    arrayType: EtsArrayType,
): TsTypedArrayElement = with(ctx) {
    initializeTypedArrayOverrides(array)
    val sort = typeToSort(arrayType.componentType)
    val nativeValue = memory.read(mkArrayIndexLValue(sort, array, index, arrayType))
    val native = TsUnresolvedValue(
        boolValue = if (sort == boolSort) nativeValue.asExpr(boolSort) else falseExpr,
        fpValue = if (sort == fp64Sort) nativeValue.asExpr(fp64Sort) else mkFp64(0.0),
        refValue = if (sort == addressSort) nativeValue.asExpr(addressSort) else mkUndefinedValue(),
        type = when (sort) {
            boolSort -> EtsFakeType.mkBool(ctx)
            fp64Sort -> EtsFakeType.mkFp(ctx)
            addressSort -> EtsFakeType.mkRef(ctx)
            else -> error("Typed array has unresolved element sort")
        },
    )
    val hasBoxed = memory.readArrayIndex(array, index, TsTypedArrayStorage.HAS_BOXED_VALUE, boolSort)
    val boxed = TsUnresolvedValue(
        boolValue = falseExpr,
        fpValue = mkFp64(0.0),
        refValue = memory.readArrayIndex(array, index, TsTypedArrayStorage.BOXED_VALUE, addressSort),
        type = EtsFakeType.mkRef(ctx),
    )

    TsTypedArrayElement(
        nativeValue = nativeValue,
        hasBoxedValue = hasBoxed,
        unresolvedValue = iteUnresolvedValue(hasBoxed, boxed, native),
    )
}

/** Initializes private engine metadata, with alias guards so an existing array's overrides survive. */
internal fun TsState.initializeTypedArrayOverrides(array: UHeapRef) = with(ctx) {
    if (array in initializedTypedArrayOverrides) return@with

    if (!isAllocatedConcreteHeapRef(array)) {
        val distinctFromInitialized = initializedTypedArrayOverrides.map { prior -> mkNot(mkHeapRefEq(array, prior)) }
        val initializeGuard = mkAnd(distinctFromInitialized)
        val empty = allocateConcreteRef()
        memory.memcpy(
            srcRef = empty,
            dstRef = array,
            type = TsTypedArrayStorage.HAS_BOXED_VALUE,
            elementSort = boolSort,
            fromSrcIdx = mkBv(0),
            fromDstIdx = mkBv(0),
            toDstIdx = mkBv(Int.MAX_VALUE),
            guard = initializeGuard,
        )
    }
    initializedTypedArrayOverrides += array
}

internal fun TsContext.boxArrayValue(scope: TsStepScope, value: UExpr<*>): UConcreteHeapRef {
    val containsFakeValue = value.sort == addressSort && value.asExpr(addressSort).hasFakeValueBranch()
    if (!containsFakeValue) return value.toFakeObject(scope)

    return scope.calcOnState {
        val packet = TsUnresolvedValue(
            boolValue = falseExpr,
            fpValue = mkFp64(0.0),
            refValue = value.asExpr(addressSort),
            type = EtsFakeType.mkRef(ctx),
        )
        mkFakeValue(scope = scope, value = packet)
    }
}
