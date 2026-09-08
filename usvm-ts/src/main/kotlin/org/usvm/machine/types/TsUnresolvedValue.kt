package org.usvm.machine.types

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef

/** The three backing payloads of a TypeScript value whose active runtime kind is not resolved yet. */
data class TsUnresolvedValue(
    val boolValue: UBoolExpr,
    val fpValue: UExpr<KFp64Sort>,
    val refValue: UHeapRef,
)
