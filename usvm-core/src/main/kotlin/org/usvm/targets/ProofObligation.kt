package org.usvm.targets

import org.usvm.UComposer
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.model.UModelBase
import org.usvm.satisfies
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

class UProofObligation<Statement, Type>(override val location: Statement, val constraints: UPathConstraints<Type>) :
    UTarget<Statement, UProofObligation<Statement, Type>>()
{
    fun weakestPrecondition(state: UState<Type, *, Statement, *, *, *>): UProofObligation<Statement, Type>? {
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
        val child = UProofObligation(state.startingStatement, childConstraints)
        child.parent = this
        return child
    }
}
