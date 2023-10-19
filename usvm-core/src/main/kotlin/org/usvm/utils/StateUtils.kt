package org.usvm.utils

import org.usvm.UState
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.USolverBase
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

/**
 * Sets the [solverResult] as a [UState.lastForkResult] for this [UState],
 * and adds its [USatResult.model] to the [UState.models] if it is a [USatResult].
 */
internal fun <Type> UState<Type, *, *, *, *, *>.updateForkResultAndModels(
    solverResult: USolverResult<UModelBase<Type>>
) {
    lastForkResult = solverResult

    if (solverResult is USatResult) {
        models += listOf(solverResult.model)
    }
}

/**
 * If this **terminated** [UState] is definitely sat (its [UState.lastForkResult] is [USatResult] or `null`), returns `true`.
 * If it is definitely unsat (its [UState.lastForkResult] is [UUnsatResult]), returns `false`.
 * Otherwise, runs [verify] with this [UState], and returns whether its [UState.lastForkResult] is [USatResult] or `null`.
 */
internal fun <Type> UState<Type, *, *, *, *, *>.isSat(): Boolean {
    var stateSolverResult = lastForkResult
    if (stateSolverResult is UUnknownResult) {
        stateSolverResult = verify()
    }

    return stateSolverResult == null || stateSolverResult is USatResult
}

/**
 * Checks [UState.pathConstraints] of this [UState] using [USolverBase.checkWithSoftConstraints],
 * sets [UState.models] with a value of a solver result if it is a [USatResult],
 * sets the solver result as a [UState.lastForkResult] for this [UState], and returns this result.
 */
private fun <Type> UState<Type, *, *, *, *, *>.verify(): USolverResult<UModelBase<Type>> {
    val solver = ctx.solver<Type>()
    val solverResult = solver.checkWithSoftConstraints(pathConstraints)

    if (solverResult is USatResult) {
        // TODO just an assignment or +=?
        models = listOf(solverResult.model)
    }

    return solverResult.also {
        lastForkResult = it
    }
}
