package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import java.util.IdentityHashMap

/**
 * A class designed to give the highest priority to the states containing exceptions.
 */
class ExceptionPropagationPathSelector<State : UState<*, *, *, *, *, State>>(
    private val selector: UPathSelector<State>,
) : UPathSelector<State> {
    // An internal queue for states containing exceptions.
    // Note that we use an identity map here to be able
    // to determine whether some state is already in the queue or not
    // without extra time consumption.
    // We use only keys from this map.
    private val exceptionalStates = IdentityHashMap<State, Nothing>()

    // An identity map of the states that were added in the internal selector.
    // That means that these states present not only in the internal queue, but in
    // the queue of the selector, and we have to process them as well.
    // We use only keys from this map.
    private val statesInSelector = IdentityHashMap<State, Nothing>()

    override fun isEmpty(): Boolean = exceptionalStates.isEmpty() && selector.isEmpty()

    /**
     * Returns an exceptional state from the internal queue if it is not empty, or
     * the result of [peek] method called on the wrapper selector.
     */
    override fun peek(): State = exceptionalStates.keys.lastOrNull() ?: selector.peek()

    override fun update(state: State): Boolean {
        val alreadyInExceptionalStates = state in exceptionalStates
        val isExceptional = state.isExceptional

        when {
            // A case when the state became exceptional, but at the
            // previous step it didn't contain any exceptions.
            // We add this exceptional state to the internal queue.
            isExceptional && !alreadyInExceptionalStates -> {
                exceptionalStates += state to null
            }
            // A case when the state contained an exception at the previous step,
            // but it doesn't contain it anymore. There are two possibilities:
            // this state was either added directly in the internal queue,
            // e.g., after a fork operation, or was transformed from a regular one.
            // In the first case, we have to add it to the selector and remove from the
            // exceptional queue, in the second one just removal is enough since
            // it was added in the selector earlier.
            !isExceptional && alreadyInExceptionalStates -> {
                exceptionalStates -= state

                if (state !in statesInSelector) {
                    if (!selector.add(state)) {
                        return false
                    }
                    statesInSelector += state to null
                    return true
                }
            }

            // Other operations don't cause queues transformations.
            else -> Unit // do nothing
        }

        // If the state is contained in the selector, we must call `update` operation for it.
        if (state in statesInSelector) {
            if (!selector.update(state)) {
                statesInSelector -= state
                return false
            }
        }

        return true
    }

    /**
     * Add the [states] in the selector. Depending on whether it
     * is exceptional or not, it will be added in the exceptional
     * state queue or in the wrapper selector.
     */
    override fun add(state: State): Boolean {
        // It if it is an exceptional state, we don't have to worry whether it
        // is contains in the selector or not. It was either already added in it,
        // or will be added later if required, when it becomes unexceptional one.
        if (state.isExceptional) {
            exceptionalStates += state to null
            return true
        } else {
            // Otherwise, we simply add it to the selector directly.
            if (!selector.add(state)) {
                return false
            }
            statesInSelector += state to null
            return true
        }
    }

    override fun remove(state: State) {
        if (state in exceptionalStates) {
            exceptionalStates -= state
        }

        if (state in statesInSelector) {
            selector.remove(state)
            statesInSelector -= state
        }
    }
}
