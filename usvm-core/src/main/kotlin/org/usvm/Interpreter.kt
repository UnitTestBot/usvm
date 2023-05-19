package org.usvm

import org.usvm.interpreter.StepResult

/**
 * An abstract [UInterpreter] used in a symbolic machine.
 */
abstract class UInterpreter<State> {
    /**
     * Interpreters a single step inside a [state].
     *
     * @return next states.
     */
    abstract fun step(state: State): StepResult<State>
}