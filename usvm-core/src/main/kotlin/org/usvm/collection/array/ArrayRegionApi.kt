package org.usvm.collection.array

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.memory.UWritableMemory
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
    contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = with(elementSort.uctx.withSizeSort<USizeSort>()) {
    val arrayValues = hashMapOf<UExpr<USizeSort>, UExpr<Sort>>()
    contents.forEachIndexed { idx, value -> arrayValues[mkSizeExpr(idx)] = value }

    val arrayLength = mkSizeExpr(arrayValues.size)
    val address = allocateArray(type, arrayLength)

    val regionId = UArrayRegionId<_, _, USizeSort>(type, elementSort)
    val region = getRegion(regionId)

    check(region is UArrayRegion<ArrayType, Sort, USizeSort>) {
        "allocateArrayInitialized is not applicable to $region"
    }

    val newRegion = region.initializeAllocatedArray(address.address, type, elementSort, arrayValues, operationGuard = trueExpr)

    setRegion(regionId, newRegion)

    return address
}

internal fun <ArrayType, USizeSort : USort> UWritableMemory<ArrayType>.allocateArray(
    type: ArrayType,
    length: UExpr<USizeSort>
): UConcreteHeapRef {
    val address = allocConcrete(type)

    val lengthRegionRef = UArrayLengthLValue<_, USizeSort>(address, type)
    write(lengthRegionRef, length, guard = length.uctx.trueExpr)

    return address
}

internal fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    contents: Sequence<UExpr<Sort>>,
) = with(sort.uctx.withSizeSort<USizeSort>()) {
    val tmpArrayRef = allocateArrayInitialized<_, _, USizeSort>(type, sort, contents)
    val contentLength: UExpr<USizeSort> = read(UArrayLengthLValue(tmpArrayRef, type))

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

    write(UArrayLengthLValue(ref, type), contentLength, guard = trueExpr)
}
