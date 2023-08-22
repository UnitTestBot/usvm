package org.usvm.api

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.memory.UMemory
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.region.UArrayIndexRef
import org.usvm.memory.collection.region.UArrayLengthRef
import org.usvm.memory.collection.region.UFieldRef
import org.usvm.types.UTypeStream
import org.usvm.uctx
import org.usvm.memory.collection.region.memcpy as memcpyInternal
import org.usvm.memory.collection.region.memset as memsetInternal
import org.usvm.memory.collection.region.allocateArray as allocateArrayInternal
import org.usvm.memory.collection.region.allocateArrayInitialized as allocateArrayInitializedInternal

fun <Type> UReadOnlyMemory<Type>.typeStreamOf(ref: UHeapRef): UTypeStream<Type> =
    types.getTypeStream(ref)

fun UMemory<*, *>.allocate() = ctx.mkConcreteHeapRef(addressCounter.freshAddress())

fun <Field, Sort : USort> UReadOnlyMemory<*>.readField(
    ref: UHeapRef, field: Field, sort: Sort
): UExpr<Sort> = read(UFieldRef(sort, ref, field))

fun <ArrayType, Sort : USort> UReadOnlyMemory<*>.readArrayIndex(
    ref: UHeapRef, index: USizeExpr, arrayType: ArrayType, sort: Sort
): UExpr<Sort> = read(UArrayIndexRef(sort, ref, index, arrayType))

fun <ArrayType> UReadOnlyMemory<*>.readArrayLength(
    ref: UHeapRef, arrayType: ArrayType
): USizeExpr = read(UArrayLengthRef(ref, arrayType))

fun <Field, Sort : USort> UWritableMemory<*>.writeField(
    ref: UHeapRef, field: Field, sort: Sort, value: UExpr<Sort>, guard: UBoolExpr
) = write(UFieldRef(sort, ref, field), value, guard)

fun <ArrayType, Sort : USort> UWritableMemory<*>.writeArrayIndex(
    ref: UHeapRef, index: USizeExpr, type: ArrayType, sort: Sort, value: UExpr<Sort>, guard: UBoolExpr
) = write(UArrayIndexRef(sort, ref, index, type), value, guard)

fun <ArrayType> UWritableMemory<*>.writeArrayLength(
    ref: UHeapRef, size: USizeExpr, arrayType: ArrayType
) = write(UArrayLengthRef(ref, arrayType), size, ref.uctx.trueExpr)


fun <ArrayType, Sort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrcIdx: USizeExpr,
    fromDstIdx: USizeExpr,
    toDstIdx: USizeExpr,
    guard: UBoolExpr,
) {
    memcpyInternal(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard)
}

fun <ArrayType, Sort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrc: USizeExpr,
    fromDst: USizeExpr,
    length: USizeExpr,
) {
    val toDst = srcRef.ctx.mkBvAddExpr(fromDst, length)
    memcpy(srcRef, dstRef, type, elementSort, fromSrc, fromDst, toDst, guard = srcRef.ctx.trueExpr)
}

fun <ArrayType, Sort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    contents: Sequence<UExpr<Sort>>
) {
    memsetInternal(ref, type, sort, contents)
}

fun <ArrayType> UWritableMemory<ArrayType>.allocateArray(
    type: ArrayType, count: USizeExpr
): UConcreteHeapRef = allocateArrayInternal(type, count)

fun <ArrayType, Sort : USort> UWritableMemory<ArrayType>.allocateArrayInitialized(
    type: ArrayType, sort: Sort, contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = allocateArrayInitializedInternal(type, sort, contents)
