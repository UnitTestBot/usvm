package org.usvm.util

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.memcpy
import org.usvm.collection.array.UArrayRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsUnresolvedArrayKind

/** Enumerates payload regions independently of whether an array was allocated or came from the input. */
internal inline fun TsContext.forEachArrayPayloadRegion(arrayType: EtsArrayType, action: (EtsType, USort) -> Unit) {
    val elementSort = typeToSort(arrayType.elementType)
    if (elementSort !is TsUnresolvedSort) {
        action(arrayDescriptorOf(arrayType), elementSort)
        return
    }

    action(EtsArrayType(EtsBooleanType, dimensions = 1), boolSort)
    action(EtsArrayType(EtsNumberType, dimensions = 1), fp64Sort)
    action(EtsArrayType(EtsUnknownType, dimensions = 1), addressSort)
}

internal fun TsState.copyArrayElements(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    arrayType: EtsArrayType,
    fromSrc: UExpr<TsSizeSort>,
    fromDst: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
) {
    ctx.forEachArrayPayloadRegion(arrayType) { region, sort ->
        memory.memcpy(srcRef, dstRef, region, sort, fromSrc, fromDst, length)
    }
    if (ctx.typeToSort(arrayType.elementType) is TsUnresolvedSort) {
        TsUnresolvedArrayKind.entries.forEach { kind ->
            memory.memcpy(srcRef, dstRef, kind, ctx.boolSort, fromSrc, fromDst, length)
        }
    }
}

/** Initializes a selector region; selectors have no separate array length or heap type. */
internal fun TsState.initializeArrayKind(
    array: UConcreteHeapRef,
    kind: TsUnresolvedArrayKind,
    values: List<UBoolExpr>,
) {
    val regionId = UArrayRegionId<TsUnresolvedArrayKind, UBoolSort, TsSizeSort>(kind, ctx.boolSort)
    val region = memory.getRegion(regionId)
    check(region is UArrayRegion<TsUnresolvedArrayKind, UBoolSort, TsSizeSort>) {
        "Cannot initialize array kind in $region"
    }

    val initialized = region.initializeAllocatedArray(
        address = array.address,
        arrayType = kind,
        sort = ctx.boolSort,
        content = values,
        operationGuard = ctx.trueExpr,
        ownership = memory.ownership,
    )
    memory.setRegion(regionId, initialized)
}
