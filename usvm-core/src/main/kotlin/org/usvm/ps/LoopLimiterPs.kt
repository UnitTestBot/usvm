package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.logger
import java.util.WeakHashMap

/**
 * Track state loop iterations number.
 * Drop states with a loop iteration number greater than [iterationLimit].
 * */
class LoopLimiterPs<Stmt, Method, Loop : Any, State : UState<*, Method, Stmt, *, *, State>>(
    private val underlyingPs: UPathSelector<State>,
    loopTracker: StateLoopTracker<Loop, Stmt, State>,
    private val iterationLimit: Int?
) : UPathSelector<State> {
    private val loopStatistic = StateLoopStatistic(loopTracker)
    private val stateStats = WeakHashMap<State, StateStats<Method, Stmt, Loop>>()

    private var lastPeekedState: State? = null
    private var lastPeekedStateStats: StateStats<Method, Stmt, Loop> = StateLoopStatistic.rootStats()

    override fun isEmpty(): Boolean = underlyingPs.isEmpty()

    override fun peek(): State = underlyingPs.peek().also {
        lastPeekedState = it
        lastPeekedStateStats = stateStats[it] ?: error("Missed state stats")
    }

    override fun add(states: Collection<State>) {
        states.forEach { addSingleState(it) }
    }

    /**
     * Add state and register it loop stats.
     *
     * Note: it is important that [state] must be initial or
     * somehow related to the [lastPeekedState] (e.g. forked from it).
     * */
    private fun addSingleState(state: State) {
        val stats = loopStatistic.updateStats(lastPeekedStateStats, state)

        val iterations = stats.maxLoopIteration
        if (iterationLimit != null && iterations > iterationLimit) {
            logger.debug {
                "Drop state ${state.id} | iteration limit exceeded $iterations at ${state.lastEnteredMethod} ${state.currentStatement}"
            }
            return
        }

        stateStats[state] = stats

        underlyingPs.add(listOf(state))
    }

    override fun update(state: State) {
        check(state === lastPeekedState) { "Try to update not peeked state" }

        underlyingPs.remove(state)
        addSingleState(state)
    }

    override fun remove(state: State) {
        check(state === lastPeekedState) { "Try to remove not peeked state" }

        lastPeekedState = null
        underlyingPs.remove(state)
    }
}
