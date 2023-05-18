package org.usvm


/**
 * An abstract symbolic analyzer.
 */
abstract class UMachine<State : UState<*, *, *, *>, Target> {
    /**
     * The main entry point. Template method for running the machine on a specified [target].
     *
     * @param target a generic target to run on.
     * @param onState called on every forked state. Can be used for collecting results.
     * @param continueAnalyzing filtering function for states. If it returns `false`, a state
     * won't be analyzed further. It is called on an original state and every forked state as well.
     * @param shouldStop called on every step, before peeking a next state from path selector. Returning `true` aborts
     * analysis.
     */
    fun run(
        target: Target,
        onState: (State) -> Unit,
        continueAnalyzing: (State) -> Boolean,
        shouldStop: () -> Boolean = { false },
    ) {
        val interpreter = getInterpreter(target)
        val pathSelector = getPathSelector(target)

        while (!pathSelector.isEmpty() && !shouldStop()) {
            pathSelector.peekAndUpdate { state ->
                val (forkedStates, stateAlive) = interpreter.step(state)

                forkedStates.forEach(onState)
                if (stateAlive) {
                    onState(state)
                }

                val originalStateAlive = stateAlive && continueAnalyzing(state)

                val nextStates = forkedStates.filter(continueAnalyzing)
                StepResult(nextStates, originalStateAlive)
            }
        }
    }

    private fun UPathSelector<State>.peekAndUpdate(step: (State) -> StepResult<State>) {
        val state = peek()
        val (forkedStates, stateAlive) = step(state)
        if (stateAlive) {
            update(state)
        } else {
            terminate(state)
        }
        add(forkedStates)
    }

    protected abstract fun getInterpreter(target: Target): UInterpreter<State>

    protected abstract fun getPathSelector(target: Target): UPathSelector<State>
}
