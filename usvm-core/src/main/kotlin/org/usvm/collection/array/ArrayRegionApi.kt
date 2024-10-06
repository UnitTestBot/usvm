package org.usvm.collection.array

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeExpr
import org.usvm.uctx
import org.usvm.withSizeSort

internal fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrcIdx: UExpr<USizeSort>,
    fromDstIdx: UExpr<USizeSort>,
    toDstIdx: UExpr<USizeSort>,
    guard: UBoolExpr,
) {
    val regionId = UArrayRegionId<_, _, USizeSort>(type, elementSort)
    val region = getRegion(regionId)

    check(region is UArrayRegion<ArrayType, Sort, USizeSort>) {
        "memcpy is not applicable to $region"
    }

    val newRegion = region.memcpy(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard)
    setRegion(regionId, newRegion)
}

internal fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.allocateArrayInitialized(
    type: ArrayType,
    elementSort: Sort,
    sizeSort: USizeSort,
    contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = elementSort.uctx.withSizeSort {
    val arrayValues = hashMapOf<UExpr<USizeSort>, UExpr<Sort>>()
    contents.forEachIndexed { idx, value -> arrayValues[mkSizeExpr(idx)] = value }

    val arrayLength = mkSizeExpr(arrayValues.size)
    val address = allocateArray(type, sizeSort, arrayLength)

    val regionId = UArrayRegionId<_, _, USizeSort>(type, elementSort)
    val region = getRegion(regionId)

    check(region is UArrayRegion<ArrayType, Sort, USizeSort>) {
        "allocateArrayInitialized is not applicable to $region"
    }

    val newRegion =
        region.initializeAllocatedArray(address.address, type, elementSort, arrayValues, operationGuard = trueExpr)

    setRegion(regionId, newRegion)

    return address
}

internal fun <ArrayType, USizeSort : USort> UWritableMemory<ArrayType>.allocateArray(
    type: ArrayType,
    sizeSort: USizeSort,
    length: UExpr<USizeSort>,
): UConcreteHeapRef {
    val address = allocConcrete(type)

    val lengthRegionRef = UArrayLengthLValue(address, type, sizeSort)
    write(lengthRegionRef, length, guard = length.uctx.trueExpr)

    return address
}

internal fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    sizeSort: USizeSort,
    contents: Sequence<UExpr<Sort>>,
) = sizeSort.uctx.withSizeSort {
    val tmpArrayRef = allocateArrayInitialized(type, sort, sizeSort, contents)
    val contentLength = read(UArrayLengthLValue(tmpArrayRef, type, sizeSort))

    memcpy(
        srcRef = tmpArrayRef,
        dstRef = ref,
        type = type,
        elementSort = sort,
        fromSrcIdx = mkSizeExpr(0),
        fromDstIdx = mkSizeExpr(0),
        toDstIdx = contentLength,
        guard = trueExpr
    )

    write(UArrayLengthLValue(ref, type, sizeSort), contentLength, guard = trueExpr)
}

/**
 * Allocates new array filled with converted elements of array at [srcRef].
 */
fun <ArrayType, SrcSort : USort, DstSort : USort, USizeSort : USort> UWritableMemory<ArrayType>.convert(
    srcType: ArrayType,
    dstType: ArrayType,
    srcRef: UHeapRef,
    srcSort: SrcSort,
    dstSort: DstSort,
    sizeSort: USizeSort,
    converter: (UExpr<SrcSort>) -> UExpr<DstSort>
): UConcreteHeapRef {
    val lengthLValue = UArrayLengthLValue(srcRef, srcType, sizeSort)
    val length = read(lengthLValue)
    val dstRef = allocateArray(dstType, sizeSort, length)
    val ctx = this.ctx.withSizeSort<USizeSort>()
    val srcReg = getRegion(UArrayRegionId<ArrayType, SrcSort, USizeSort>(srcType, srcSort))
            as UArrayRegion<ArrayType, SrcSort, USizeSort>
    val dstReg = getRegion(UArrayRegionId<ArrayType, DstSort, USizeSort>(dstType, dstSort))
            as UArrayRegion<ArrayType, DstSort, USizeSort>
    val guard = ctx.trueExpr
    convertArray(ctx, srcType, dstType, srcSort, dstSort, srcReg, dstReg, srcRef, dstRef, length, guard, converter)
    return dstRef
}

/**
 * Allocates new array filled with converted elements of array at [srcRef].
 */
fun <ArrayType, SrcSort : USort, DstSort : USort, USizeSort : USort> UWritableMemory<ArrayType>.convert(
    srcType: ArrayType,
    dstType: ArrayType,
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    srcSort: SrcSort,
    dstSort: DstSort,
    sizeSort: USizeSort,
    guard: UBoolExpr,
    converter: (UExpr<SrcSort>) -> UExpr<DstSort>
) {
    val srcReg = getRegion(UArrayRegionId<ArrayType, SrcSort, USizeSort>(srcType, srcSort))
            as UArrayRegion<ArrayType, SrcSort, USizeSort>
    val dstReg = getRegion(UArrayRegionId<ArrayType, DstSort, USizeSort>(dstType, dstSort))
            as UArrayRegion<ArrayType, DstSort, USizeSort>
    val lengthLValue = UArrayLengthLValue(srcRef, srcType, sizeSort)
    val length = read(lengthLValue)
    val ctx = this.ctx.withSizeSort<USizeSort>()
    convertArray(ctx, srcType, dstType, srcSort, dstSort, srcReg, dstReg, srcRef, dstRef, length, guard, converter)
}
