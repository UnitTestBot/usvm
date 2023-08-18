package org.usvm.api

import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.memory.UMemory
import org.usvm.memory.memcpy as memcpyInternal

fun <ArrayType, Sort : USort> UMemory<*, *>.memcpy(
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
