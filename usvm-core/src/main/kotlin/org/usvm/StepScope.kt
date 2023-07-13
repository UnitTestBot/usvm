package org.usvm

/**
 * An auxiliary class, which carefully maintains forks and asserts via [fork] and [assert].
 * It should be created on every step in an interpreter.
 * You can think about an instance of [StepScope] as a monad `ExceptT null (State [T])`.
 *
 * An underlying state is `null`, iff one of the `condition`s passed to the [fork] was unsatisfiable.
 *
 * To execute some function on a state, you should use [doWithState] or [calcOnState]. `null` is returned, when
 * the current state is `null`.
 *
 * @param originalState an initial state.
 */
class StepScope<T : UState<Type, Field, *, *>, Type, Field>(
    originalState: T,
) {
    private val forkedStates = mutableListOf<T>()
    private var curState: T? = originalState
    var alive: Boolean = true
        private set

    /**
     * @return forked states and the status of initial state.
     */
    fun stepResult() = StepResult(forkedStates.asSequence(), alive)

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
     * If the [condition] is unsatisfiable, sets underlying state to `null`.
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

    fun forkMulti(conditionsWithBlocks: List<Pair<UBoolExpr, (T) -> Unit?>>) {
        val state = curState ?: return

        val conditionStates = fork(state, conditionsWithBlocks.map { it.first })
        val conditionStatesWithBlocks = conditionsWithBlocks.map { it.second }.zip(conditionStates)
        val forkedStates = conditionStatesWithBlocks.mapNotNull { forkResultWithBlock ->
            val positiveState = forkResultWithBlock.second
            val block = forkResultWithBlock.first

            positiveState?.let {
                block(it)

                positiveState
            }
        }

        val firstForkedState = forkedStates.firstOrNull()
        val otherStates = forkedStates.filter { it !== firstForkedState }

        this.forkedStates += otherStates

        curState = firstForkedState
    }
}

/**
 * @param forkedStates new states forked from the original state.
 * @param originalStateAlive indicates whether the original state is still alive or not.
 */
class StepResult<T>(
    val forkedStates: Sequence<T>,
    val originalStateAlive: Boolean,
) {
    operator fun component1() = forkedStates
    operator fun component2() = originalStateAlive
}
