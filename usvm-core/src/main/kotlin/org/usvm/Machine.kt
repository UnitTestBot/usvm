package org.usvm


/**
 * An abstract symbolic machine.
 */
abstract class UMachine<State : UState<*, *, *, *>, Target> {
    /**
     * The main entry point. Template method for running the machine on a specified [target].
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
            val state = pathSelector.peek()
            val (forkedStates, stateAlive) = interpreter.step(state)

            forkedStates.forEach(onState)
            if (stateAlive) {
                onState(state)
            }

            if (!stateAlive || !continueAnalyzing(state)) {
                pathSelector.terminate()
            }

            val nextStates = forkedStates.asSequence().filter(continueAnalyzing)
            pathSelector.add(nextStates)
        }
    }

    protected abstract fun getInterpreter(target: Target): UInterpreter<State>

    protected abstract fun getPathSelector(target: Target): UPathSelector<State>
}
