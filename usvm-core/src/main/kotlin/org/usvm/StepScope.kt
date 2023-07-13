package org.usvm

/**
 * An auxiliary class, which carefully maintains forks and asserts via [fork] and [assert].
 * It should be created on every step in an interpreter.
 * You can think about an instance of [StepScope] as a monad `ExceptT null (State [T])`.
 *
 * TODO: fix comment An underlying state is `null`, iff one of the `condition`s passed to the [fork] was unsatisfiable.
 *
 * To execute some function on a state, you should use [doWithState] or [calcOnState]. `null` is returned, when
 * the current state is `null`.
 *
 * @param originalState an initial state.
 */
class StepScope<T : UState<Type, Field, *, *>, Type, Field>(
    private val originalState: T,
) {
    private val forkedStates = mutableListOf<T>()

    private var alive: Boolean = true

    private var canProcessFurtherOnCurrentStep: Boolean = true

    /**
     * @return forked states and the status of initial state.
     */
    fun stepResult() = StepResult(forkedStates.asSequence(), alive)

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`.
     */
    fun doWithState(block: T.() -> Unit): Unit? =
        if (canProcessFurtherOnCurrentStep) {
            originalState.block()
        } else {
            null
        }

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`, otherwise returns result of calling [block].
     */
    fun <R> calcOnState(block: T.() -> R): R? =
        if (canProcessFurtherOnCurrentStep) {
            originalState.block()
        } else {
            null
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
        check(canProcessFurtherOnCurrentStep)

        val (posState, negState) = fork(originalState, condition)

        posState?.blockOnTrueState()

        if (posState == null) {
            canProcessFurtherOnCurrentStep = false
            check(negState === originalState)
        } else {
            check(posState === originalState)
        }

        if (negState != null) {
            negState.blockOnFalseState()
            if (negState !== originalState) {
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
        check(canProcessFurtherOnCurrentStep)

        val (posState, _) = fork(originalState, constraint)

        posState?.block()

        if (posState == null) {
            alive = false
            canProcessFurtherOnCurrentStep = false
        }

        return posState?.let { }
    }

    // TODO docs
    // TODO think about merging it with fork above
    fun forkMulti(conditionsWithBlockOnStates: List<Pair<UBoolExpr, T.() -> Unit>>) {
        check(canProcessFurtherOnCurrentStep)

        val conditions = conditionsWithBlockOnStates.map { it.first }

        val conditionStates = fork(originalState, conditions)

        val forkedStates = conditionStates.mapIndexedNotNull { idx, positiveState ->
            val block = conditionsWithBlockOnStates[idx].second

            positiveState?.apply(block)
        }

        canProcessFurtherOnCurrentStep = false
        if (forkedStates.isEmpty()) {
            alive = false
            return
        }
        require(forkedStates.first() == originalState)

        this.forkedStates += forkedStates.subList(1, forkedStates.size)
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
