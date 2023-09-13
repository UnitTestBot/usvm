package org.usvm

import org.usvm.StepScope.StepScopeState.CANNOT_BE_PROCESSED
import org.usvm.StepScope.StepScopeState.CAN_BE_PROCESSED
import org.usvm.StepScope.StepScopeState.DEAD

/**
 * An auxiliary class, which carefully maintains forks and asserts via [fork] and [assert].
 * It should be created on every step in an interpreter.
 * You can think about an instance of [StepScope] as a monad `ExceptT null (State [T])`.
 *
 * This scope is considered as [DEAD], iff the condition in [assert] was unsatisfiable or unknown.
 * The underlying state cannot be processed further (see [CANNOT_BE_PROCESSED]),
 * if the first passed to [fork] or [forkMulti] condition was unsatisfiable or unknown.
 *
 * To execute some function on a state, you should use [doWithState] or [calcOnState]. `null` is returned, when
 * this scope cannot be processed on the current step - see [CANNOT_BE_PROCESSED].
 *
 * @param originalState an initial state.
 */
class StepScope<T : UState<Type, *, *, Context, *, T>, Type, Context : UContext>(
    private val originalState: T,
) {
    private val forkedStates = mutableListOf<T>()

    private inline val alive: Boolean get() = stepScopeState != DEAD
    private inline val canProcessFurtherOnCurrentStep: Boolean get() = stepScopeState == CAN_BE_PROCESSED

    /**
     * Determines whether we interact this scope on the current step.
     * @see [StepScopeState].
     */
    private var stepScopeState: StepScopeState = CAN_BE_PROCESSED

    /**
     * @return forked states and the status of initial state.
     */
    fun stepResult() = StepResult(forkedStates.asSequence(), alive)

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`.
     */
    fun doWithState(block: T.() -> Unit) {
        check(canProcessFurtherOnCurrentStep) { "Caller should check before processing the current hop further" }
        return originalState.block()
    }

    /**
     * Executes [block] on a state.
     *
     * @return `null` if the underlying state is `null`, otherwise returns result of calling [block].
     */
    fun <R> calcOnState(block: T.() -> R): R {
        check(canProcessFurtherOnCurrentStep) { "Caller should check before processing the current hop further" }
        return originalState.block()
    }

    /**
     * Forks on a [condition], performing [blockOnTrueState] on a state satisfying [condition] and
     * [blockOnFalseState] on a state satisfying [condition].not().
     *
     * If the [condition] is unsatisfiable or unknown, sets the scope state to the [CANNOT_BE_PROCESSED].
     *
     * @return `null` if the [condition] is unsatisfiable or unknown.
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
            stepScopeState = CANNOT_BE_PROCESSED
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

    /**
     * Forks on a few disjoint conditions using `forkMulti` in `State.kt`
     * and executes the corresponding block on each not-null state.
     *
     * NOTE: always sets the [stepScopeState] to the [CANNOT_BE_PROCESSED] value.
     */
    fun forkMulti(conditionsWithBlockOnStates: List<Pair<UBoolExpr, T.() -> Unit>>) {
        check(canProcessFurtherOnCurrentStep)

        val conditions = conditionsWithBlockOnStates.map { it.first }

        val conditionStates = forkMulti(originalState, conditions)

        val forkedStates = conditionStates.mapIndexedNotNull { idx, positiveState ->
            val block = conditionsWithBlockOnStates[idx].second

            positiveState?.apply(block)
        }

        stepScopeState = CANNOT_BE_PROCESSED
        if (forkedStates.isEmpty()) {
            stepScopeState = DEAD
            return
        }

        val firstForkedState = forkedStates.first()
        require(firstForkedState == originalState) {
            "The original state $originalState was expected to become the first of forked states but $firstForkedState found"
        }

        // Interpret the first state as original and others as forked
        this.forkedStates += forkedStates.subList(1, forkedStates.size)
    }

    fun assert(
        constraint: UBoolExpr,
        block: T.() -> Unit = {},
    ): Unit? {
        check(canProcessFurtherOnCurrentStep)

        val (posState) = forkMulti(originalState, listOf(constraint))

        posState?.block()

        if (posState == null) {
            stepScopeState = DEAD
        }

        return posState?.let { }
    }

    /**
     * Represents the current state of this [StepScope].
     */
    private enum class StepScopeState {
        /**
         * Cannot be processed further with any actions.
         */
        DEAD,
        /**
         * Cannot be forked or asserted using [fork], [forkMulti] or [assert],
         * but is considered as alive from the Machine's point of view.
         */
        CANNOT_BE_PROCESSED,
        /**
         * Can be forked using [fork] or [forkMulti] and asserted using [assert].
         */
        CAN_BE_PROCESSED;
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
