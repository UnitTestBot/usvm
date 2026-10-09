package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.solver.UUnsatResult

/** Own properties of allocated literals and symbolic inputs use different initial-presence rules. */
internal fun TsContext.resolvePropertyReceiver(scope: TsStepScope, ref: UHeapRef): UHeapRef? {
    if (ref !is UIteExpr<*>) return ref

    fun infeasible(condition: UBoolExpr): Boolean = scope.calcOnState {
        val alternative = clone()
        alternative.pathConstraints += condition
        solver<EtsType>().check(alternative.pathConstraints) is UUnsatResult
    }

    val trueRef = ref.trueBranch.asExpr(addressSort)
    val falseRef = ref.falseBranch.asExpr(addressSort)
    if (infeasible(ref.condition)) return resolvePropertyReceiver(scope, falseRef)
    if (infeasible(mkNot(ref.condition))) return resolvePropertyReceiver(scope, trueRef)

    // Retain both feasible receivers. The false state repeats the operation with its proven branch.
    scope.fork(ref.condition) ?: return null
    return resolvePropertyReceiver(scope, trueRef)
}
