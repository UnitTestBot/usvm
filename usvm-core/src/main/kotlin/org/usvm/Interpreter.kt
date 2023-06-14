package org.usvm

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

    override fun toString(): String = this::class.simpleName?:"<empty>"
}