package org.usvm

import mu.KLogging
import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy
import org.usvm.util.bracket
import org.usvm.util.debug
import org.usvm.utils.isSat

val logger = object : KLogging() {}.logger

/**
 * An abstract symbolic machine.
 *
 * @see [run]
 */
abstract class UMachine<State : UState<*, *, *, *, *, State>> : AutoCloseable {
    abstract val options: UMachineOptions

    /**
     * Runs symbolic execution loop.
     *
     * @param interpreter interpreter instance used to make symbolic execution steps.
     * @param pathSelector path selector instance used to peek the next state to execute.
     * @param observer abstract symbolic execution events listener. Can be used for statistics and
     * results collection.
     * @param isStateTerminated filtering function for states. If it returns `false`, a state
     * won't be analyzed further. It is called on an original state and every forked state as well.
     * @param stopStrategy is called on every step, before peeking a next state from the path selector.
     * Returning `true` aborts analysis.
     */
    protected fun run(
        interpreter: UInterpreter<State>,
        pathSelector: UPathSelector<State>,
        observer: UMachineObserver<State>,
        isStateTerminated: (State) -> Boolean,
        stopStrategy: StopStrategy = StopStrategy { false }
    ): UMachineRunResult {
        return logger.debug().bracket("$this.run($interpreter, ${pathSelector::class.simpleName})") {
            val forkLimit = options.forkLimit
            var generatedForks = 0UL
            fun forkLimitReached(): Boolean = forkLimit != null && generatedForks >= forkLimit

            observer.onMachineStarted()
            val result = try {
                while (!pathSelector.isEmpty() && !forkLimitReached() && !stopStrategy.shouldStop()) {
                    val state = pathSelector.peek()
                    observer.onStatePeeked(state)

                    val (forkedStates, stateAlive) = try {
                        interpreter.step(state)
                    } catch (e: Throwable) {
                        if (options.throwExceptionOnStepFailure) {
                            throw e
                        }

                        logger.error(e) { "Step failed" }
                        observer.onState(state, forks = emptySequence())
                        pathSelector.remove(state)
                        observer.onStateTerminated(state, stateReachable = false)
                        continue
                    }

                    observer.onState(state, forkedStates)

                    val originalStateAlive = stateAlive && !isStateTerminated(state)
                    val aliveForkedStates = mutableListOf<State>()
                    for (forkedState in forkedStates) {
                        generatedForks++
                        if (!isStateTerminated(forkedState)) {
                            aliveForkedStates.add(forkedState)
                        } else {
                            // TODO: distinguish between states terminated by exception (runtime or user) and
                            //  those which just exited
                            if (forkedState.isSat()) {
                                observer.onStateTerminated(forkedState, stateReachable = true)
                            }
                        }
                    }

                    if (originalStateAlive) {
                        pathSelector.update(state)
                    } else {
                        pathSelector.remove(state)
                        if (state.isSat()) {
                            observer.onStateTerminated(state, stateReachable = stateAlive)
                        }
                    }

                    if (aliveForkedStates.isNotEmpty()) {
                        pathSelector.add(aliveForkedStates)
                    }
                }

                val stopReason = when {
                    pathSelector.isEmpty() -> UMachineStopReason.EXHAUSTED
                    forkLimitReached() -> UMachineStopReason.FORK_LIMIT
                    else -> UMachineStopReason.STOPPED
                }
                UMachineRunResult(stopReason = stopReason, generatedForks = generatedForks, forkLimit = forkLimit)
            } finally {
                observer.onMachineStopped()
            }

            logger.debug {
                val detail = if (result.stopReason == UMachineStopReason.STOPPED) stopStrategy.stopReason() else ""
                "Stop reason: ${result.stopReason}; $detail; generated forks: ${result.generatedForks}; " +
                    "fork limit: $forkLimit"
            }
            result
        }
    }

    override fun toString(): String = this::class.simpleName?:"<empty>"
}
