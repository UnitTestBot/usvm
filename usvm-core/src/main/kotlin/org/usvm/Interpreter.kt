package org.usvm

import org.usvm.model.UModel

/**
 * An abstract [UInterpreter] used in a symbolic machine.
 */
abstract class UInterpreter<State : UState<*, *, *, *>>(
    open val ctx: UContext,
) {
    /**
     * Interpreters a single step inside a [state].
     *
     * @return next states.
     */
    fun step(state: State): StepResult<State> {
        val scope = StepScope(ctx, state, ::findModel)
        step(scope)
        return scope.forkingResult()
    }

    protected abstract fun findModel(pc: UPathCondition): UModel?

    protected abstract fun step(scope: StepScope<State>)
}