package org.usvm.machine.types

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext

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

/** Join both payloads and kind selectors without allocating wrapper identities for either branch. */
internal fun TsContext.iteUnresolvedValue(
    condition: UBoolExpr,
    trueValue: TsUnresolvedValue,
    falseValue: TsUnresolvedValue,
): TsUnresolvedValue {
    val type = EtsFakeType(
        boolTypeExpr = mkIte(condition, trueValue.type.boolTypeExpr, falseValue.type.boolTypeExpr),
        fpTypeExpr = mkIte(condition, trueValue.type.fpTypeExpr, falseValue.type.fpTypeExpr),
        refTypeExpr = mkIte(condition, trueValue.type.refTypeExpr, falseValue.type.refTypeExpr),
    )
    return TsUnresolvedValue(
        boolValue = mkIte(condition, trueValue.boolValue, falseValue.boolValue),
        fpValue = mkIte(condition, trueValue.fpValue, falseValue.fpValue),
        refValue = mkIte(condition, trueValue.refValue, falseValue.refValue),
        type = type,
    )
}
