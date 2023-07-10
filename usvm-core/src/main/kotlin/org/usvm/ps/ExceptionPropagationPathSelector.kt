package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState

/**
 * A class designed to give the highest priority to the states containing exceptions.
 */
class ExceptionPropagationPathSelector<State : UState<*, *, *, *>>(
    private val selector: UPathSelector<State>,
) : UPathSelector<State> {
    // An internal queue for states containing exceptions
    private val exceptionalStates: MutableList<State> = mutableListOf()

    // A set containing hash codes of the identities of the states
    // containing in the `exceptionalStates`. It is required
    // to be able to determine whether a particular state is in the queue.
    private val exceptionalStatesHashCodes: MutableSet<Int> = mutableSetOf()

    // A set of hash codes of the states that were added in the internal selector.
    // That means that these states present not only in the internal queue, but in
    // the queue of the selector, and we have to process them as well.
    private val statesInSelectorHashCodes: MutableSet<Int> = mutableSetOf()

    override fun isEmpty(): Boolean = exceptionalStates.isEmpty() && selector.isEmpty()

    /**
     * Returns an exceptional state from the internal queue if it is not empty, or
     * the result of [peek] method called on the wrapper selector.
     */
    override fun peek(): State = exceptionalStates.lastOrNull() ?: selector.peek()

    override fun update(state: State) {
        val stateHashCode = System.identityHashCode(state)
        val alreadyInExceptionalStates = stateHashCode in exceptionalStatesHashCodes
        val isExceptional = state.isExceptional

        when {
            // A case when the state became exceptional, but at the
            // previous step it didn't contain any exceptions.
            // We add this exceptional state to the internal queue.
            isExceptional && !alreadyInExceptionalStates -> {
                exceptionalStatesHashCodes += stateHashCode
                exceptionalStates += state
            }
            // A case when the state contained an exception at the previous step,
            // but it doesn't contain it anymore. There are two possibilities:
            // this state was either added directly in the internal queue,
            // e.g., after a fork operation, or was transformed from a regular one.
            // In the first case, we have to add it to the selector and remove from the
            // exceptional queue, in the second one just removal is enough since
            // it was added in the selector earlier.
            !isExceptional && alreadyInExceptionalStates -> {
                exceptionalStatesHashCodes -= stateHashCode
                exceptionalStates -= state

                if (stateHashCode !in statesInSelectorHashCodes) {
                    selector.add(listOf(state))
                    statesInSelectorHashCodes += stateHashCode
                }
            }

            // Other operations don't cause queues transformations.
            else -> Unit // do nothing
        }

        // If the state is contained in the selector, we must call `update` operation for it.
        if (stateHashCode in statesInSelectorHashCodes) {
            selector.update(state)
        }
    }

    /**
     * Add the [states] in the selector. Depending on whether it
     * is exceptional or not, it will be added in the exceptional
     * state queue or in the wrapper selector.
     */
    override fun add(states: Collection<State>) {
        val statesToAdd = mutableListOf<State>()

        states.forEach {
            val identityHashCode = System.identityHashCode(it)

            // It if it is an exceptional state, we don't have to worry whether it
            // is contains in the selector or not. It was either already added in it,
            // or will be added later if required, when it becomes unexceptional one.
            if (it.isExceptional) {
                exceptionalStates += it
                exceptionalStatesHashCodes += identityHashCode
            } else {
                // Otherwise, we simply add it to the selector directly.
                statesToAdd += it
                statesInSelectorHashCodes += identityHashCode
            }
        }

        selector.add(statesToAdd)
    }

    override fun remove(state: State) {
        val stateHashCode = System.identityHashCode(state)

        if (stateHashCode in exceptionalStatesHashCodes) {
            exceptionalStates -= state
            exceptionalStatesHashCodes -= stateHashCode
        }

        if (stateHashCode in statesInSelectorHashCodes) {
            selector.remove(state)
            statesInSelectorHashCodes.remove(stateHashCode)
        }
    }
}