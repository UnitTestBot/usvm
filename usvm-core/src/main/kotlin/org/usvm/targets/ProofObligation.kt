package org.usvm.targets

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.splitModelsByCondition

object UProofObligation {
    fun <Type> weakestPrecondition(
        constraints: UPathConstraints<Type>,
        state: UState<Type, *, *, *, *, *>
    ): UPathConstraints<Type>? {
        val composer = UComposer(state.ctx, state.memory)
        val satisfyingModels = state.models.toMutableSet()
        val childConstraints = state.pathConstraints.clone()
        childConstraints.mappedUnion(constraints) {
            val result = composer.compose(it)
            // By construction, [state.models] satisfy all constraints in [state.pathConstraints].
            // Here we check if some model in [state] satisfies all new constraints added in [mappedUnion].
            satisfyingModels.removeIf { !it.satisfies(result) }
            result
        }

        if (satisfyingModels.isEmpty()) {
            val solver = state.ctx.solver<Type>()
            val satResult = solver.check(childConstraints)
            when (satResult) {
                is USatResult<UModelBase<Type>> ->
                    state.models += satResult.model

                is UUnsatResult<*>,
                is UUnknownResult<*> ->
                    return null
            }
        }

        return childConstraints
    }

    private fun <Type> UModelBase<Type>.satisfies(expr: UBoolExpr): Boolean =
        splitModelsByCondition(listOf(this), expr).trueModels.isNotEmpty()
}
