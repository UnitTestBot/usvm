package org.usvm.util

import io.ksmt.sort.KBv16Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.IntermediateLValueField
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.tctx
import org.usvm.machine.state.TsState
import org.usvm.memory.URegisterStackLValue
import org.usvm.sizeSort
import org.usvm.solver.UUnsatResult
import org.usvm.types.singleOrNull

/** Local type widening does not change the regions backing an array. */
internal fun TsState.arrayStorageType(ref: UHeapRef, staticType: EtsType): EtsType {
    val memoryType = referenceStorageType(ref)
    return if (memoryType is EtsArrayType || memoryType == EtsStringType || isAllocatedConcreteHeapRef(ref)) {
        memoryType ?: staticType
    } else {
        staticType
    }
}

private fun TsState.referenceStorageType(ref: UHeapRef): EtsType? = when (ref) {
    is UNullRef -> null
    is UIteExpr<*> -> conditionalStorageType(ref)
    is UConcreteHeapRef, is USymbolicHeapRef -> memory.typeStreamOf(ref).singleOrNull() ?: EtsUnknownType
    else -> null
}

private fun TsState.conditionalStorageType(ref: UIteExpr<*>): EtsType? {
    val trueType = referenceStorageType(ref.trueBranch.asExpr(ctx.addressSort))
    val falseType = referenceStorageType(ref.falseBranch.asExpr(ctx.addressSort))
    val types = listOfNotNull(trueType, falseType).distinct()
    if (types.size <= 1) return types.singleOrNull()

    // Alias-dependent writes may leave an inactive unknown payload in the other branch.
    // Use path proof, rather than one cached model, to discard that alternative.
    val falseBranchState = clone()
    falseBranchState.pathConstraints += ctx.mkNot(ref.condition)
    if (ctx.solver<EtsType>().check(falseBranchState.pathConstraints) is UUnsatResult) return trueType

    val trueBranchState = clone()
    trueBranchState.pathConstraints += ref.condition
    return if (ctx.solver<EtsType>().check(trueBranchState.pathConstraints) is UUnsatResult) falseType else null
}

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    field: IntermediateLValueField,
): UFieldLValue<IntermediateLValueField, Sort> = UFieldLValue(sort, ref, field)

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    field: String,
): UFieldLValue<String, Sort> = UFieldLValue(sort, ref, field)

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    field: EtsFieldSignature,
): UFieldLValue<String, Sort> = mkFieldLValue(sort, ref, field.name)

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    field: EtsField,
): UFieldLValue<String, Sort> = mkFieldLValue(sort, ref, field.signature)

fun <Sort : USort> mkArrayIndexLValue(
    sort: Sort,
    ref: UHeapRef,
    index: UExpr<TsSizeSort>,
    type: EtsArrayType,
): UArrayIndexLValue<EtsType, Sort, TsSizeSort> = with(ref.tctx) {
    val descriptor = arrayDescriptorOf(type)
    UArrayIndexLValue(sort, ref, index, descriptor)
}

fun mkArrayLengthLValue(
    ref: UHeapRef,
    type: EtsArrayType,
): UArrayLengthLValue<EtsType, TsSizeSort> = with(ref.tctx) {
    val descriptor = arrayDescriptorOf(type)
    return UArrayLengthLValue(ref, descriptor, sizeSort)
}

internal fun mkStringBackingLengthLValue(
    ref: UHeapRef,
): UArrayLengthLValue<EtsType, TsSizeSort> = with(ref.tctx) {
    UArrayLengthLValue(ref, stringBackingArrayDescriptor, sizeSort)
}

private object StringBackingField

/** Internal string contents never share a field region with TypeScript properties. */
internal fun mkStringBackingLValue(ref: UHeapRef): UFieldLValue<*, UAddressSort> =
    UFieldLValue(ref.tctx.addressSort, ref, StringBackingField)

internal fun mkStringBackingElementLValue(
    ref: UHeapRef,
    index: UExpr<TsSizeSort>,
): UArrayIndexLValue<EtsType, KBv16Sort, TsSizeSort> = with(ref.tctx) {
    UArrayIndexLValue(bv16Sort, ref, index, stringBackingArrayDescriptor)
}

fun <Sort : USort> mkRegisterStackLValue(
    sort: Sort,
    idx: Int,
): URegisterStackLValue<Sort> = URegisterStackLValue(sort, idx)
