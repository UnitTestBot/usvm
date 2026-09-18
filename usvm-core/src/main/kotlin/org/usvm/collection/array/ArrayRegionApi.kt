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

internal fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.initializeArray(
    arrayHeapRef: UConcreteHeapRef,
    type: ArrayType,
    elementSort: Sort,
    sizeSort: USizeSort,
    contents: Sequence<UExpr<Sort>>
) = elementSort.uctx.withSizeSort {
    val arrayValues = contents.toList()
    val arrayLength = mkSizeExpr(arrayValues.size)
    initializeArrayLength(arrayHeapRef, type, sizeSort, arrayLength)

    val regionId = UArrayRegionId<_, _, USizeSort>(type, elementSort)
    val region = getRegion(regionId)

    check(region is UArrayRegion<ArrayType, Sort, USizeSort>) {
        "allocateArrayInitialized is not applicable to $region"
    }

    val newRegion = region.initializeAllocatedArray(
        arrayHeapRef.address,
        type,
        elementSort,
        arrayValues,
        operationGuard = trueExpr,
        ownership = ownership
    )

    setRegion(regionId, newRegion)
}

internal fun <ArrayType, USizeSort : USort> UWritableMemory<ArrayType>.initializeArrayLength(
    arrayHeapRef: UConcreteHeapRef,
    type: ArrayType,
    sizeSort: USizeSort,
    length: UExpr<USizeSort>,
) {
    val lengthRegionRef = UArrayLengthLValue(arrayHeapRef, type, sizeSort)
    write(lengthRegionRef, length, guard = length.uctx.trueExpr)
}

internal fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    sizeSort: USizeSort,
    contents: Sequence<UExpr<Sort>>,
) = sizeSort.uctx.withSizeSort {
    val tmpArrayRef = allocConcrete(type)
    initializeArray(tmpArrayRef, type, sort, sizeSort, contents)
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
