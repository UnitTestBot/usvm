package org.usvm


/**
 * An abstract symbolic analyzer.
 */
abstract class UAnalyzer<State : UState<*, *, *, *>, Target> {
    /**
     * The main entry point. Template method for running the analyzer on a specified [target].
     *
     * @param target a generic target to run on.
     * @param onState called on every forked state. Can be used for collecting results.
     * @param continueAnalyzing filtering function for forked states. If it returns `false`, state
     * isn't added to path selector.
     * @param shouldStop checked on every step, before peeking a next state from path selector. Returning `true` aborts
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
            val state = pathSelector.peek()
            val (forkedStates, stateAlive) = interpreter.step(state)

            forkedStates.forEach(onState)
            if (stateAlive) {
                onState(state)
            }

            var originalState: State? = state
            if (!stateAlive || !continueAnalyzing(state)) {
                pathSelector.terminate(state)
                originalState = null
            }

            val nextStates = forkedStates.filter(continueAnalyzing)
            pathSelector.add(originalState, nextStates)
        }
    }

    protected abstract fun getInterpreter(target: Target): UInterpreter<State>

    protected abstract fun getPathSelector(target: Target): UPathSelector<State>
}
