package org.usvm.interpreter

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.fork

/**
 * An auxiliary class, which carefully maintains forks and asserts via [fork] and [assert].
 * It should be created on every step in interepreter.
 * You can think about an instance of [StepScope] as a monad State.
 *
 * An underlying state is `null`, iff one of the `condition`s passed to the [fork] was unsatisfiable.
 *
 * To execute some function on a state, you should use [doWithState] or [calcOnState]. `null` is returned, when
 * the current state is `null`.
 *
 * @param initialState an initial state.
 */
class StepScope(
    val uctx: UContext,
    initialState: ExecutionState,
) {
    private val accumulatedStates = mutableListOf<ExecutionState>()
    private var curState: ExecutionState? = initialState

    /**
     * @return all states satisfying appropriate `!condition` collected in forks.
     */
    fun forkedStates(): List<ExecutionState> = accumulatedStates

    fun allStates() = forkedStates() + listOfNotNull(curState)

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`.
     */
    fun doWithState(block: ExecutionState.() -> Unit): Unit? {
        val state = curState ?: return null
        state.block()
        return Unit
    }

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`, otherwise returns result of calling [block].
     */
    fun <T> calcOnState(block: ExecutionState.() -> T): T? {
        val state = curState ?: return null
        return state.block()
    }

    /**
     * Forks on a [condition], performing [blockOnTrueState] on a state satisfying [condition] and
     * [blockOnFalseState] on a state satisfying [condition].not().
     *
     * If the [condition], sets underlying state to `null`.
     *
     * @return `null` if the [condition] is unsatisfiable.
     */
    fun fork(
        condition: UBoolExpr,
        blockOnTrueState: ExecutionState.() -> Unit = {},
        blockOnFalseState: ExecutionState.() -> Unit = {},
    ): Unit? {
        val state = curState ?: return null

        val (posState, negState) = fork(state, condition)

        posState?.blockOnTrueState()
        curState = posState

        if (negState != null) {
            negState.blockOnFalseState()
            accumulatedStates += negState
        }

        // conversion of ExecutionState? to Unit?
        return posState?.let { }
    }

    fun assert(
        constraint: UBoolExpr,
        block: ExecutionState.() -> Unit = {},
    ): Unit? {
        val state = curState ?: return null

        val (posState, _) = fork(state, constraint)

        posState?.block()
        curState = posState

        return posState?.let { }
    }
}
