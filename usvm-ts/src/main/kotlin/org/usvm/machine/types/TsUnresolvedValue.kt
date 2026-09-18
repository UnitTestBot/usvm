package org.usvm.machine.types

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef

/** The backing payloads and kind selectors of a TypeScript value with an unresolved runtime kind. */
data class TsUnresolvedValue(
    val boolValue: UBoolExpr,
    val fpValue: UExpr<KFp64Sort>,
    val refValue: UHeapRef,
    val type: EtsFakeType,
)
