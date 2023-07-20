package org.usvm.interpreter

import org.usvm.UPathSelector
import org.usvm.language.types.PythonType
import org.usvm.types.UTypeStream

class PythonVirtualPathSelector(
    private val basePathSelector: UPathSelector<PythonExecutionState>,
    private val pathSelectorForStatesWithDelayedForks: UPathSelector<PythonExecutionState>
): UPathSelector<PythonExecutionState> {
    private val delayedForks = mutableSetOf<DelayedForkWithTypeRating>()

    override fun isEmpty(): Boolean {
        return basePathSelector.isEmpty() && delayedForks.isEmpty() && pathSelectorForStatesWithDelayedForks.isEmpty()
    }

    override fun peek(): PythonExecutionState {
        if (!basePathSelector.isEmpty()) {
            val result = basePathSelector.peek()
            result.extractedFrom = basePathSelector
            return result
        }
        if (delayedForks.isNotEmpty()) {
            TODO()

        } else if (!pathSelectorForStatesWithDelayedForks.isEmpty()) {
            val result = pathSelectorForStatesWithDelayedForks.peek()
            result.extractedFrom = pathSelectorForStatesWithDelayedForks
            return result

        } else {
            error("Not reachable")
        }
    }

    override fun update(state: PythonExecutionState) {
        if (state.wasExecuted) {
            state.extractedFrom?.remove(state)
            state.delayedForks.forEach {
                delayedForks.add(DelayedForkWithTypeRating(it, state.makeTypeRating(it)))
            }

        } else {
            state.extractedFrom?.update(state)
        }
    }

    override fun add(states: Collection<PythonExecutionState>) {
        states.forEach { state ->
            if (state.wasExecuted)
                return@forEach
            if (state.delayedForks.isEmpty()) {
                basePathSelector.add(listOf(state))
            } else {
                pathSelectorForStatesWithDelayedForks.add(listOf(state))
            }
        }
    }

    override fun remove(state: PythonExecutionState) {
        state.extractedFrom?.remove(state)
    }

}

data class DelayedForkWithTypeRating(
    val delayedFork: DelayedFork,
    val typeRating: UTypeStream<PythonType>
)