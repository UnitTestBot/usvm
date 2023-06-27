package org.usvm

import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy

/**
 * An abstract symbolic machine.
 *
 * @see [run]
 */
abstract class UMachine<State> : AutoCloseable {
    /**
     * Runs symbolic execution loop.
     *
     * @param interpreter interpreter instance used to make symbolic execution steps.
     * @param pathSelector path selector instance used to peek the next state to execute.
     * @param observer abstract symbolic execution events listener. Can be used for statistics and
     * results collection.
     * @param continueAnalyzing filtering function for states. If it returns `false`, a state
     * won't be analyzed further. It is called on an original state and every forked state as well.
     * @param stopStrategy is called on every step, before peeking a next state from the path selector.
     * Returning `true` aborts analysis.
     */
    // TODO: Cannot it be protected?
    fun run(
        interpreter: UInterpreter<State>,
        pathSelector: UPathSelector<State>,
        observer: UMachineObserver<State>,
        continueAnalyzing: (State) -> Boolean,
        stopStrategy: StopStrategy = StopStrategy { false }
    ) {
        while (!pathSelector.isEmpty() && !stopStrategy.shouldStop()) {
            val state = pathSelector.peek()
            val (forkedStates, stateAlive) = interpreter.step(state)

            observer.onState(state, forkedStates)

            val originalStateAlive = stateAlive && continueAnalyzing(state)
            val aliveForkedStates = mutableListOf<State>()
            for (forkedState in forkedStates) {
                if (continueAnalyzing(forkedState)) {
                    aliveForkedStates.add(forkedState)
                } else {
                    // TODO: distinguish between states terminated by exception (runtime or user) and
                    //  those which just exited
                    observer.onStateTerminated(forkedState)
                }
            }

            if (originalStateAlive) {
                pathSelector.update(state)
            } else {
                pathSelector.remove(state)
                observer.onStateTerminated(state)
            }
            pathSelector.add(aliveForkedStates)
        }
    }
}
