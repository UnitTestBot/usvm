package org.usvm

import org.usvm.model.UModel
import org.usvm.solver.USolver

/**
 * An auxiliary class, which carefully maintains forks and asserts via [fork] and [assert].
 * It should be created on every step in interepreter.
 * You can think about an instance of [StepScope] as a monad `ExceptT null (State [T])`.
 *
 * An underlying state is `null`, iff one of the `condition`s passed to the [fork] was unsatisfiable.
 *
 * To execute some function on a state, you should use [doWithState] or [calcOnState]. `null` is returned, when
 * the current state is `null`.
 *
 * @param initialState an initial state.
 */
class StepScope<T : UState<*, *, *, *>>(
    val uctx: UContext,
    initialState: T,
) {
    private val forkedStates = mutableListOf<T>()
    private var curState: T? = initialState
    private var alive: Boolean = true

    /**
     * @return forked states and the status of initial state.
     */
    fun forkingResult() = StepResult(forkedStates, alive)

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`.
     */
    fun doWithState(block: T.() -> Unit): Unit? {
        val state = curState ?: return null
        state.block()
        return Unit
    }

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`, otherwise returns result of calling [block].
     */
    fun <R> calcOnState(block: T.() -> R): R? {
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
        blockOnTrueState: T.() -> Unit = {},
        blockOnFalseState: T.() -> Unit = {},
    ): Unit? {
        val state = curState ?: return null

        val (posState, negState) = fork(state, condition)

        posState?.blockOnTrueState()
        curState = posState

        if (negState != null) {
            negState.blockOnFalseState()
            if (negState !== state) {
                forkedStates += negState
            }
        }

        // conversion of ExecutionState? to Unit?
        return posState?.let { }
    }

    fun assert(
        constraint: UBoolExpr,
        block: T.() -> Unit = {},
    ): Unit? {
        val state = curState ?: return null

        val (posState, _) = fork(state, constraint)

        posState?.block()
        curState = posState

        if (posState == null) {
            alive = false
        }

        return posState?.let { }
    }
}

/**
 * @param forkedStates states satisfying negative branches of conditions.
 * @param initialStateAlive indicates whether initial state is still alive or not.
 */
class StepResult<T>(
    val forkedStates: List<T>,
    val initialStateAlive: Boolean,
) {
    operator fun component1() = forkedStates
    operator fun component2() = initialStateAlive
}
