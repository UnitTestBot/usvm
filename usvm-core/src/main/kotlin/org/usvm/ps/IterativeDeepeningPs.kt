package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.logger
import java.util.WeakHashMap

/**
 * Track state loop iterations number.
 * Always prefer states with less loop iterations number.
 * Drop states with a loop iteration number greater than [iterationLimit].
 * */
class IterativeDeepeningPs<Stmt, Method, Loop : Any, State : UState<*, Method, Stmt, *, *, State>>(
    private val underlyingPs: UPathSelector<State>,
    loopTracker: StateLoopTracker<Loop, Stmt, State>,
    private val iterationLimit: Int?
) : UPathSelector<State> {
    private val loopStatistic = StateLoopStatistic(loopTracker)
    private val stateStats = WeakHashMap<State, StateStats<Method, Stmt, Loop>>()

    private var currentLevel = 0
    private var maxLevel = 0

    private val pendingStates = hashMapOf<Int, MutableList<State>>()

    private var lastPeekedState: State? = null
    private var lastPeekedStateStats: StateStats<Method, Stmt, Loop> = StateLoopStatistic.rootStats()

    override fun isEmpty(): Boolean {
        pushStates()
        return underlyingPs.isEmpty()
    }

    override fun peek(): State {
        pushStates()
        return underlyingPs.peek().also {
            lastPeekedState = it
            lastPeekedStateStats = stateStats[it] ?: error("Missed state stats")
        }
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
            logger.debug { "Drop state ${state.id} | iteration limit exceeded $iterations" }
            return
        }

        stateStats[state] = stats

        when {
            iterations == currentLevel -> addStateToCurrentLevel(state)
            iterations > currentLevel -> addStateToFutureLevel(state, iterations)
            else -> addStateToPreviousLevel(state, iterations)
        }
    }

    override fun update(state: State) {
        check(state === lastPeekedState) { "Try to update not peeked state" }

        underlyingPs.remove(state)
        addSingleState(state)
    }

    override fun remove(state: State) {
        check(state === lastPeekedState) { "Try to remove not peeked state" }

        underlyingPs.remove(state)
    }

    private fun pushStates() {
        while (underlyingPs.isEmpty() && currentLevel <= maxLevel) {
            currentLevel++
            val states = pendingStates[currentLevel] ?: continue
            underlyingPs.add(states)
            states.clear()
        }
    }

    private fun addStateToCurrentLevel(state: State) {
        underlyingPs.add(listOf(state))
    }

    private fun addStateToFutureLevel(state: State, level: Int) {
        maxLevel = maxOf(level, maxLevel)
        val pending = pendingStates.getOrPut(level) { mutableListOf() }
        pending.add(state)
    }

    private fun addStateToPreviousLevel(state: State, level: Int) {
        val currentLevelStates = pendingStates.getOrPut(currentLevel) { mutableListOf() }
        while (!underlyingPs.isEmpty()) {
            val st = underlyingPs.peek()
            underlyingPs.remove(st)
            currentLevelStates.add(st)
        }

        currentLevel = level
        addStateToCurrentLevel(state)
    }
}
