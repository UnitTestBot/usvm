package org.usvm.machine.ps

import org.usvm.UPathSelector
import org.usvm.machine.PyState
import kotlin.random.Random


class WeightedPyPathSelector(
    private val random: Random,
    private val proportionalToSelectorSize: Boolean,
    private val counter: (PyState) -> Int,
    private val weight: (Int) -> Double,
    private val baseBaseSelectorCreation: () -> UPathSelector<PyState>,
) : UPathSelector<PyState> {
    private val selectors = mutableMapOf<Int, UPathSelector<PyState>>()
    private val selectorSizes = mutableMapOf<Int, Int>()
    private val countOfState = mutableMapOf<PyState, Int?>()

    override fun isEmpty(): Boolean {
        return selectors.all { it.value.isEmpty() }
    }

    override fun peek(): PyState {
        val availableNumbers = selectors.mapNotNull { (number, selector) ->
            if (selector.isEmpty()) null else number
        }
        val chosenNumber = weightedRandom(random, availableNumbers) {
            if (proportionalToSelectorSize) {
                weight(it) * (selectorSizes[it] ?: error("$it not in selectorSizes"))
            } else {
                weight(it)
            }
        }
        val selector = selectors[chosenNumber] ?: error("$chosenNumber not in selectors")
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
        val count = counter(state)
        if (selectors[count] == null) {
            selectors[count] = baseBaseSelectorCreation()
            selectorSizes[count] = 0
        }
        val selector = selectors[count] ?: error("$count not in selectors")
        selector.add(listOf(state))
        countOfState[state] = count
        selectorSizes[count] = (selectorSizes[count] ?: error("$count not in selectorSizes")) + 1
    }

    override fun remove(state: PyState) {
        val oldCount = countOfState[state] ?: error("State was not in path selector")
        val selector = selectors[oldCount] ?: error("State was not in path selector")
        selector.remove(state)
        countOfState[state] = null
        selectorSizes[oldCount] = (selectorSizes[oldCount] ?: error("$oldCount not in selectorSizes")) - 1
    }
}
