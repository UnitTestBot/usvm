package org.usvm.merging

import mu.KotlinLogging
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.ps.ExecutionTreeTracker

val logger = KotlinLogging.logger { }

class MergingPathSelector<State : UState<*, *, *, *, *, State>>(
    private val underlyingPathSelector: UPathSelector<State>,
    private val executionTreeTracker: ExecutionTreeTracker<State, *>,
    private val closeStatesSearcher: CloseStatesSearcher<State>,
    private val advanceLimit: Int = 15
) : UPathSelector<State> {
    override fun isEmpty(): Boolean = underlyingPathSelector.isEmpty()

    private sealed interface SelectorState {
        class Advancing(var steps: Int) : SelectorState
        object Peeking : SelectorState
    }

    private var selectorState: SelectorState = SelectorState.Advancing(0)

    override fun peek(): State {
        val state = underlyingPathSelector.peek()

        val resultState = when (val selectorState = selectorState) {
            is SelectorState.Advancing -> {
                val closeState = closeStatesSearcher.findCloseStates(state).firstOrNull() ?: return state
                val mergedState = state.mergeWith(closeState, Unit)
                if (mergedState == null) {
                    selectorState.steps++
                    if (selectorState.steps == advanceLimit) {
                        this.selectorState = SelectorState.Peeking
                    }
                    logger.debug { "Advance state: $closeState to $state" }
                    return closeState
                }
                selectorState.steps = 0

                logger.debug { "Merged states: $state + $closeState == $mergedState" }

                remove(state)
                remove(closeState)
                add(listOf(mergedState))

                mergedState
            }

            is SelectorState.Peeking -> {
                this.selectorState = SelectorState.Advancing(0)
                state
            }
        }
        return resultState
    }

    override fun update(state: State) {
        underlyingPathSelector.update(state)
        executionTreeTracker.update(state)
    }

    override fun add(states: Collection<State>) {
        underlyingPathSelector.add(states)
        executionTreeTracker.add(states)
    }

    override fun remove(state: State) {
        underlyingPathSelector.remove(state)
        executionTreeTracker.remove(state)
    }
}