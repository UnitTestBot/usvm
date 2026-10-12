package org.usvm.util

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readArrayIndex
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.machine.types.iteUnresolvedValue
import org.usvm.memory.UReadOnlyMemory

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
internal fun TsContext.readTypedArrayElement(
    memory: UReadOnlyMemory<*>,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    arrayType: EtsArrayType,
): TsTypedArrayElement {
    val sort = typeToSort(arrayType.elementType)
    val nativeValue = memory.read(mkArrayIndexLValue(sort, array, index, arrayType))
    val native = TsUnresolvedValue(
        boolValue = if (sort == boolSort) nativeValue.asExpr(boolSort) else falseExpr,
        fpValue = if (sort == fp64Sort) nativeValue.asExpr(fp64Sort) else mkFp64(0.0),
        refValue = if (sort == addressSort) nativeValue.asExpr(addressSort) else mkUndefinedValue(),
        type = when (sort) {
            boolSort -> EtsFakeType.mkBool(this)
            fp64Sort -> EtsFakeType.mkFp(this)
            addressSort -> EtsFakeType.mkRef(this)
            else -> error("Typed array has unresolved element sort")
        },
    )
    val allocated = isAllocatedConcreteHeapRef(array)
    val hasBoxed = if (allocated) {
        memory.readArrayIndex(array, index, TsTypedArrayStorage.HAS_BOXED_VALUE, boolSort)
    } else {
        falseExpr
    }
    val boxed = TsUnresolvedValue(
        boolValue = falseExpr,
        fpValue = mkFp64(0.0),
        refValue = if (allocated) {
            memory.readArrayIndex(array, index, TsTypedArrayStorage.BOXED_VALUE, addressSort)
        } else {
            mkUndefinedValue()
        },
        type = EtsFakeType.mkRef(this),
    )

    return TsTypedArrayElement(
        nativeValue = nativeValue,
        hasBoxedValue = hasBoxed,
        unresolvedValue = iteUnresolvedValue(hasBoxed, boxed, native),
    )
}

internal fun TsContext.boxArrayValue(scope: TsStepScope, value: UExpr<*>): UConcreteHeapRef {
    val containsFakeValue = value.sort == addressSort && value.asExpr(addressSort).hasFakeValueBranch()
    if (!containsFakeValue) return value.toFakeObject(scope)

    return scope.calcOnState {
        mkFakeValue(
            scope = scope,
            value = TsUnresolvedValue(
                boolValue = falseExpr,
                fpValue = mkFp64(0.0),
                refValue = value.asExpr(addressSort),
                type = EtsFakeType.mkRef(ctx),
            ),
        )
    }
}
