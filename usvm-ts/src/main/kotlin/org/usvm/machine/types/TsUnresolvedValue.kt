package org.usvm.machine.types

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef

/**
 * A read-only snapshot of payloads and kind selectors, without a heap identity or allocation.
 * [mkFakeValue] materializes it as a fake wrapper and constrains its kind through a live execution scope.
 */
data class TsUnresolvedValue(
    val boolValue: UBoolExpr,
    val fpValue: UExpr<KFp64Sort>,
    val refValue: UHeapRef,
    val type: EtsFakeType,
)
