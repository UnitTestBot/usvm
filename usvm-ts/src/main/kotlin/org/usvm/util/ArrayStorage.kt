package org.usvm.util

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.memcpy
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsUnresolvedArrayKind

/** Enumerates storage channels, independently of whether an array was allocated or came from the input. */
internal inline fun TsContext.forEachArrayStorageRegion(arrayType: EtsArrayType, action: (Any, USort) -> Unit) {
    val elementSort = typeToSort(arrayType.elementType)
    if (elementSort !is TsUnresolvedSort) {
        action(arrayDescriptorOf(arrayType), elementSort)
        return
    }

    action(EtsArrayType(EtsBooleanType, dimensions = 1), boolSort)
    action(EtsArrayType(EtsNumberType, dimensions = 1), fp64Sort)
    action(EtsArrayType(EtsUnknownType, dimensions = 1), addressSort)
    TsUnresolvedArrayKind.entries.forEach { action(it, boolSort) }
}

internal fun TsState.copyArrayElements(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    arrayType: EtsArrayType,
    fromSrc: UExpr<TsSizeSort>,
    fromDst: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
) {
    ctx.forEachArrayStorageRegion(arrayType) { region, sort ->
        memory.memcpy(srcRef, dstRef, region, sort, fromSrc, fromDst, length)
    }
}
