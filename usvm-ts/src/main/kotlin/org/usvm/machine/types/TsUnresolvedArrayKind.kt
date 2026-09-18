package org.usvm.machine.types

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readArrayIndex
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.memory.UReadOnlyMemory

/** Kind selectors live with input elements, so copying elements also preserves their runtime types. */
internal enum class TsUnresolvedArrayKind {
    BOOLEAN,
    NUMBER,
}

internal fun TsContext.readUnresolvedArrayElement(
    memory: UReadOnlyMemory<*>,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
): TsUnresolvedValue {
    val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
    val boolArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
    val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
    val boolKind = memory.readArrayIndex(array, index, TsUnresolvedArrayKind.BOOLEAN, boolSort)
    val fpKind = memory.readArrayIndex(array, index, TsUnresolvedArrayKind.NUMBER, boolSort)
    // Default allocated cells represent references (undefined), including cells written with complete fake wrappers.
    val refKind = mkNot(mkOr(boolKind, fpKind))
    val type = EtsFakeType(
        boolTypeExpr = boolKind,
        fpTypeExpr = fpKind,
        refTypeExpr = refKind,
    )
    val boolValue = memory.readArrayIndex(array, index, boolArrayType, boolSort)
    val fpValue = memory.readArrayIndex(array, index, numberArrayType, fp64Sort)
    val refValue = memory.readArrayIndex(array, index, unknownArrayType, addressSort)

    return TsUnresolvedValue(
        boolValue = boolValue,
        fpValue = fpValue,
        refValue = refValue,
        type = type,
    )
}
