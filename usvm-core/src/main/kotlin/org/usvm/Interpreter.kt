package org.usvm

import org.usvm.model.UModel
import org.usvm.solver.USolver

/**
 * An abstract [UInterpreter] used in a symbolic analyzer.
 */
abstract class UInterpreter<State : UState<*, *, *, *>>(
    open val ctx: UContext,
    protected val solver: USolver<UPathCondition, UModel>
) {
    /**
     * Interpreters a single step inside a [state].
     *
     * @return next states.
     */
    fun step(state: State): StepResult<State> {
        val scope = StepScope(ctx, solver, state)
        step(scope)
        return scope.forkingResult()
    }

    protected abstract fun step(scope: StepScope<State>)
}