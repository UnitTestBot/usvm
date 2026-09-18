package org.usvm.machine.types

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.memory.UReadOnlyMemory
import org.usvm.util.mkArrayIndexLValue

/** Kind selectors live with input elements, so copying elements also preserves their runtime types. */
internal enum class TsUnresolvedArrayKind {
    BOOLEAN,
    NUMBER,
    REFERENCE,
}

internal fun TsContext.readUnresolvedArrayElement(
    memory: UReadOnlyMemory<*>,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
): TsUnresolvedValue {
    val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
    val refValue = memory.read(mkArrayIndexLValue(addressSort, array, index, unknownArrayType))

    // Allocated unresolved arrays store complete wrappers, including conditional writes, in the address region.
    if (isAllocatedConcreteHeapRef(array)) {
        return TsUnresolvedValue(
            boolValue = falseExpr,
            fpValue = mkFp64(0.0),
            refValue = refValue,
            type = EtsFakeType.mkRef(this),
        )
    }

    val boolArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
    val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
    val boolKind = memory.read(UArrayIndexLValue(boolSort, array, index, TsUnresolvedArrayKind.BOOLEAN))
    val fpKind = memory.read(UArrayIndexLValue(boolSort, array, index, TsUnresolvedArrayKind.NUMBER))
    val refKind = memory.read(UArrayIndexLValue(boolSort, array, index, TsUnresolvedArrayKind.REFERENCE))
    val type = EtsFakeType(boolTypeExpr = boolKind, fpTypeExpr = fpKind, refTypeExpr = refKind)
    val boolValue = memory.read(mkArrayIndexLValue(boolSort, array, index, boolArrayType))
    val fpValue = memory.read(mkArrayIndexLValue(fp64Sort, array, index, numberArrayType))

    return TsUnresolvedValue(
        boolValue = boolValue,
        fpValue = fpValue,
        refValue = refValue,
        type = type,
    )
}
