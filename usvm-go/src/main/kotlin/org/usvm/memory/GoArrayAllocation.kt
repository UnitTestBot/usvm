package org.usvm.memory

import org.jacodb.go.api.GoType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.initializeArray
import org.usvm.api.initializeArrayLength

internal fun <SizeSort : USort> UWritableMemory<GoType>.allocateGoArray(
    type: GoType,
    sizeSort: SizeSort,
    count: UExpr<SizeSort>,
): UConcreteHeapRef {
    val reference = allocConcrete(type)
    initializeArrayLength(reference, type.arrayStorageType(), sizeSort, count)
    return reference
}

internal fun <Sort : USort, SizeSort : USort> UWritableMemory<GoType>.allocateGoArrayInitialized(
    type: GoType,
    sort: Sort,
    sizeSort: SizeSort,
    contents: Sequence<UExpr<Sort>>,
): UConcreteHeapRef {
    val reference = allocConcrete(type)
    initializeArray(reference, type.arrayStorageType(), sort, sizeSort, contents)
    return reference
}
