package org.usvm.util

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.IntermediateLValueField
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.tctx
import org.usvm.memory.URegisterStackLValue
import org.usvm.sizeSort

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

fun <Sort : USort> mkRegisterStackLValue(
    sort: Sort,
    idx: Int,
): URegisterStackLValue<Sort> = URegisterStackLValue(sort, idx)
