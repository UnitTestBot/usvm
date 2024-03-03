package org.usvm.machine.ps

import org.usvm.UPathSelector
import org.usvm.machine.PyState
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.rendering.PyObjectModelBuilder
import org.usvm.python.model.PyTupleObject
import org.usvm.python.model.calculateNumberOfMocks
import kotlin.math.max
import kotlin.random.Random


class WeightedByNumberOfVirtualPathSelector(
    private val random: Random,
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
        val chosenNumber = weightedRandom(random, availableNumbers) { mocks -> 1.0 / max(1, mocks + 1) }
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
        val numberOfVirtual = calculateNumberOfVirtual(state)
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

    private fun calculateNumberOfVirtual(state: PyState): Int =
        runCatching {
            val modelHolder = PyModelHolder(state.pyModel)
            val builder = PyObjectModelBuilder(state, modelHolder)
            val models = state.inputSymbols.map { symbol ->
                val interpreted = interpretSymbolicPythonObject(modelHolder, state.memory, symbol)
                builder.convert(interpreted)
            }
            val tupleOfModels = PyTupleObject(models)
            calculateNumberOfMocks(tupleOfModels)
        }.getOrDefault(2)
}