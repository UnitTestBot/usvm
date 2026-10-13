package org.usvm.memory

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.SliceType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.writeArrayIndex
import org.usvm.api.writeArrayLength
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying

// Arrays, slices and strings share storage by element type. Headers retain their Go type.
internal fun GoType.arrayStorageType(): GoType = when (val type = underlying()) {
    is ArrayType -> {
        SliceType(type.elementType)
    }
    GoBasicTypes.STRING -> {
        SliceType(GoBasicTypes.UINT8)
    }
    else -> {
        type
    }
}

internal fun <Sort : USort, SizeSort : USort> UReadOnlyMemory<*>.readGoArrayIndex(
    reference: UHeapRef,
    index: UExpr<SizeSort>,
    type: GoType,
    sort: Sort,
): UExpr<Sort> = readArrayIndex(reference, index, type.arrayStorageType(), sort)

internal fun <SizeSort : USort> UReadOnlyMemory<*>.readGoArrayLength(
    reference: UHeapRef,
    type: GoType,
    sizeSort: SizeSort,
): UExpr<SizeSort> = readArrayLength(reference, type.arrayStorageType(), sizeSort)

internal fun <Sort : USort, SizeSort : USort> UWritableMemory<*>.writeGoArrayIndex(
    reference: UHeapRef,
    index: UExpr<SizeSort>,
    type: GoType,
    sort: Sort,
    value: UExpr<Sort>,
    guard: UBoolExpr,
) = writeArrayIndex(reference, index, type.arrayStorageType(), sort, value, guard)

internal fun <SizeSort : USort> UWritableMemory<*>.writeGoArrayLength(
    reference: UHeapRef,
    length: UExpr<SizeSort>,
    type: GoType,
    sizeSort: SizeSort,
) = writeArrayLength(reference, length, type.arrayStorageType(), sizeSort)

internal fun <Sort : USort, SizeSort : USort> UWritableMemory<*>.copyGoArray(
    source: UHeapRef,
    destination: UHeapRef,
    type: GoType,
    sort: Sort,
    sourceOffset: UExpr<SizeSort>,
    destinationOffset: UExpr<SizeSort>,
    length: UExpr<SizeSort>,
) = memcpy(source, destination, type.arrayStorageType(), sort, sourceOffset, destinationOffset, length)
