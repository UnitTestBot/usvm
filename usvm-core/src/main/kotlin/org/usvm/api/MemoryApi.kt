package org.usvm.api

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.types.UTypeStream
import org.usvm.uctx
import org.usvm.withSizeSort
import org.usvm.collection.array.allocateArray as allocateArrayInternal
import org.usvm.collection.array.allocateArrayInitialized as allocateArrayInitializedInternal
import org.usvm.collection.array.memcpy as memcpyInternal
import org.usvm.collection.array.memset as memsetInternal

fun <Type> UReadOnlyMemory<Type>.typeStreamOf(ref: UHeapRef): UTypeStream<Type> =
    types.getTypeStream(ref)

fun UContext<*>.allocateConcreteRef(): UConcreteHeapRef = mkConcreteHeapRef(addressCounter.freshAllocatedAddress())
fun UContext<*>.allocateStaticRef(): UConcreteHeapRef = mkConcreteHeapRef(addressCounter.freshStaticAddress())

fun <Field, Sort : USort> UReadOnlyMemory<*>.readField(
    ref: UHeapRef, field: Field, sort: Sort
): UExpr<Sort> = read(UFieldLValue(sort, ref, field))

fun <ArrayType, Sort : USort, USizeSort : USort> UReadOnlyMemory<*>.readArrayIndex(
    ref: UHeapRef, index: UExpr<USizeSort>, arrayType: ArrayType, sort: Sort
): UExpr<Sort> = read(UArrayIndexLValue(sort, ref, index, arrayType))

fun <ArrayType, USizeSort : USort> UReadOnlyMemory<*>.readArrayLength(
    ref: UHeapRef, arrayType: ArrayType, sizeSort: USizeSort
): UExpr<USizeSort> = read(UArrayLengthLValue(ref, arrayType, sizeSort))

fun <Field, Sort : USort> UWritableMemory<*>.writeField(
    ref: UHeapRef, field: Field, sort: Sort, value: UExpr<Sort>, guard: UBoolExpr
) = write(UFieldLValue(sort, ref, field), value, guard)

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.writeArrayIndex(
    ref: UHeapRef, index: UExpr<USizeSort>, type: ArrayType, sort: Sort, value: UExpr<Sort>, guard: UBoolExpr
) = write(UArrayIndexLValue(sort, ref, index, type), value, guard)

fun <ArrayType, USizeSort : USort> UWritableMemory<*>.writeArrayLength(
    ref: UHeapRef, size: UExpr<USizeSort>, arrayType: ArrayType, sizeSort: USizeSort
) = write(UArrayLengthLValue(ref, arrayType, sizeSort), size, ref.uctx.trueExpr)


fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrcIdx: UExpr<USizeSort>,
    fromDstIdx: UExpr<USizeSort>,
    toDstIdx: UExpr<USizeSort>,
    guard: UBoolExpr,
) {
    memcpyInternal(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard)
}

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrc: UExpr<USizeSort>,
    fromDst: UExpr<USizeSort>,
    length: UExpr<USizeSort>,
) {
    val toDst = srcRef.uctx.withSizeSort { mkSizeAddExpr(fromDst, mkSizeSubExpr(length, mkSizeExpr(1))) }
    memcpy(srcRef, dstRef, type, elementSort, fromSrc, fromDst, toDst, guard = srcRef.ctx.trueExpr)
}

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    sizeSort: USizeSort,
    contents: Sequence<UExpr<Sort>>
) {
    memsetInternal(ref, type, sort, sizeSort, contents)
}

fun <ArrayType, USizeSort : USort> UWritableMemory<ArrayType>.allocateArray(
    type: ArrayType, sizeSort: USizeSort, count: UExpr<USizeSort>,
): UConcreteHeapRef = allocateArrayInternal(type, sizeSort, count)

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.allocateArrayInitialized(
    type: ArrayType, sort: Sort, sizeSort: USizeSort, contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = allocateArrayInitializedInternal(type, sort, sizeSort, contents)
