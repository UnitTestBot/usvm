package org.usvm.util

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
import org.usvm.model.TsArrayType
import org.usvm.model.TsFieldSignature
import org.usvm.sizeSort

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    field: IntermediateLValueField,
): UFieldLValue<IntermediateLValueField, Sort> = UFieldLValue(sort, ref, field)

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    field: TsFieldSignature,
): UFieldLValue<String, Sort> = UFieldLValue(sort, ref, field.name)

fun <Sort : USort> mkFieldLValue(
    sort: Sort,
    ref: UHeapRef,
    fieldName: String,
): UFieldLValue<String, Sort> = UFieldLValue(sort, ref, fieldName)

fun <Sort : USort> mkArrayIndexLValue(
    sort: Sort,
    ref: UHeapRef,
    index: UExpr<TsSizeSort>,
    type: TsArrayType,
): UArrayIndexLValue<TsArrayType, Sort, TsSizeSort> = UArrayIndexLValue(sort, ref, index, type)

fun mkArrayLengthLValue(
    ref: UHeapRef,
    type: TsArrayType,
): UArrayLengthLValue<TsArrayType, TsSizeSort> = UArrayLengthLValue(ref, type, ref.tctx.sizeSort)

fun <Sort : USort> mkRegisterStackLValue(
    sort: Sort,
    idx: Int,
): URegisterStackLValue<Sort> = URegisterStackLValue(sort, idx)
