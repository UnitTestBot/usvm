package org.usvm.utils

import org.usvm.UBoolExpr
import org.usvm.UState
import org.usvm.isTrue
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.USolverBase
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

/**
 * If this **terminated** [UState] is definitely sat (its [UState.models] are not empty), returns `true`.
 * Otherwise, runs [verify] with this [UState], and returns whether the solver result is [USatResult].
 */
internal fun <Type> UState<Type, *, *, *, *, *>.isSat(): Boolean {
    if (models.isNotEmpty()) {
        return true
    }

    return verify() is USatResult
}

@Suppress("MoveVariableDeclarationIntoWhen")
internal fun <Type, State : UState<Type, *, *, *, *, State>> State.checkSat(condition: UBoolExpr): State? {
    val conditionalState = clone()
    conditionalState.pathConstraints += condition

    // If this state did not fork at all or was sat at the last fork point, it must be still sat, so we can just
    // check this condition with presented models
    if (conditionalState.models.isNotEmpty()) {
        val trueModels = conditionalState.models.filter { it.eval(condition).isTrue }

        if (trueModels.isNotEmpty()) {
            return conditionalState
        }
    }

    val solver = conditionalState.ctx.solver<Type>()
    val solverResult = solver.check(conditionalState.pathConstraints)

    return when (solverResult) {
        is USatResult -> {
            conditionalState.models += solverResult.model

            // If state with the added condition is satisfiable, it means that the original state is satisfiable too,
            // and we can save a model from the solver
            models += solverResult.model

            conditionalState
        }
        is UUnknownResult, is UUnsatResult -> null
    }
}

/**
 * Checks [UState.pathConstraints] of this [UState] using [USolverBase.checkWithSoftConstraints],
 * sets [UState.models] with a value of a solver result if it is a [USatResult], and returns this result.
 */
private fun <Type> UState<Type, *, *, *, *, *>.verify(): USolverResult<UModelBase<Type>> {
    val solver = ctx.solver<Type>()
    val solverResult = solver.checkWithSoftConstraints(pathConstraints)

    if (solverResult is USatResult) {
        // TODO just an assignment or +=?
        models = listOf(solverResult.model)
    }

    return solverResult
}
