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

    val newRegion = region.memcpy(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard, ownership)
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

    val newRegion = region.initializeAllocatedArray(
        address.address,
        type,
        elementSort,
        arrayValues,
        operationGuard = trueExpr,
        ownership = ownership
    )

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
