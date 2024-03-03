package org.usvm.machine.ps

import org.usvm.UPathSelector
import org.usvm.machine.PyState
import kotlin.random.Random


class WeightedPyPathSelector(
    private val random: Random,
    private val counter: (PyState) -> Int,
    private val weight: (Int) -> Double,
    private val baseBaseSelectorCreation: () -> UPathSelector<PyState>
): UPathSelector<PyState> {
    private val selectors = mutableMapOf<Int, UPathSelector<PyState>>()
    private val selectorOfState = mutableMapOf<PyState, UPathSelector<PyState>?>()

    override fun isEmpty(): Boolean {
        return selectors.all { it.value.isEmpty() }
    }

    override fun peek(): PyState {
        val availableNumbers = selectors.mapNotNull { (number, selector) ->
            if (selector.isEmpty()) null else number
        }
        val chosenNumber = weightedRandom(random, availableNumbers, weight)
        val selector = selectors[chosenNumber]!!
        return selector.peek()
    }

    override fun update(state: PyState) {
        remove(state)
        add(state)
    }

    override fun add(states: Collection<PyState>) {
        states.forEach { add(it) }
    }

    private fun add(state: PyState) {
        val numberOfVirtual = counter(state)
        if (selectors[numberOfVirtual] == null)
            selectors[numberOfVirtual] = baseBaseSelectorCreation()
        val selector = selectors[numberOfVirtual]!!
        selector.add(listOf(state))
        selectorOfState[state] = selector
    }

    override fun remove(state: PyState) {
        val selector = selectorOfState[state] ?: error("State was not in path selector")
        selector.remove(state)
        selectorOfState[state] = null
    }
}