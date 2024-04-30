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
abstract class UMachine<State : UState<*, *, *, *, *, *>> : AutoCloseable {
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
    ) {
        logger.debug().bracket("$this.run($interpreter, ${pathSelector::class.simpleName})") {
            var stepsCount: Long = 0

            while (!pathSelector.isEmpty() && !stopStrategy.shouldStop()) {
                stepsCount++
                val state = pathSelector.peek()

                val (forkedStates, stateAlive) = try {
                    interpreter.step(state)
                } catch (ex: Exception) {
                    logger.error(ex) { "Machine step failed:" }
                    continue
                }

                val stats = (state as UState<*, *, *, *, *, *>).ctx.stats

                if (!stateAlive) {
                    stats.dead++
                }

                if (stepsCount % 1_000 == 0L) {
                    logger.info { "STATS: $stats" }
                }

                observer.onState(state, forkedStates)

                val originalStateAlive = stateAlive && !isStateTerminated(state)
                val aliveForkedStates = mutableListOf<State>()
                for (forkedState in forkedStates) {
                    if (!isStateTerminated(forkedState)) {
                        aliveForkedStates.add(forkedState)
                    } else {
                        stats.terminated++

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
                    stats.terminated++
                    stats.current--

                    pathSelector.remove(state)
                    if (state.isSat()) {
                        observer.onStateTerminated(state, stateReachable = stateAlive)
                    }
                }

                if (aliveForkedStates.isNotEmpty()) {
                    stats.current += aliveForkedStates.size
                    pathSelector.add(aliveForkedStates)
                }
            }

            if (!pathSelector.isEmpty()) {
                logger.debug { stopStrategy.stopReason() }
            }
        }
    }

    override fun toString(): String = this::class.simpleName?:"<empty>"
}
