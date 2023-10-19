package org.usvm.utils

import org.usvm.UState
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult

internal fun <Type> UState<Type, *, *, *, *, *>.updateForkResultAndModels(
    solverResult: USolverResult<UModelBase<Type>>
) {
    lastForkResult = solverResult

    if (solverResult is USatResult) {
        models += listOf(solverResult.model)
    }
}

// TODO docs
internal fun <Type> UState<Type, *, *, *, *, *>.verify(): USolverResult<UModelBase<Type>> {
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
