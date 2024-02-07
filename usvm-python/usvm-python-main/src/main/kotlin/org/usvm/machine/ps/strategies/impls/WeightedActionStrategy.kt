package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import org.usvm.machine.ps.strategies.PyPathSelectorActionStrategy
import org.usvm.machine.ps.weightedRandom
import kotlin.random.Random

class WeightedActionStrategy<DFState: DelayedForkState, DFGraph: DelayedForkGraph<DFState>>(
    private val random: Random,
    private val actions: List<Action<DFState, DFGraph>>
): PyPathSelectorActionStrategy<DFState, DFGraph> {
    override fun chooseAction(graph: DFGraph): PyPathSelectorAction<DFState>? {
        val availableActions = actions.filter {
            it.isAvailable(graph)
        }
        if (availableActions.isEmpty()) {
            return null
        }
        logger.debug("Available actions: {}", availableActions)
        val action = weightedRandom(random, availableActions) { it.weight }
        logger.debug("Making action {}", action)
        return action.makeAction(graph, random)
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}

abstract class Action<DFState: DelayedForkState, in DFGraph: DelayedForkGraph<DFState>>(
    val weight: Double
) {
    abstract fun isAvailable(graph: DFGraph): Boolean
    abstract fun makeAction(graph: DFGraph, random: Random): PyPathSelectorAction<DFState>
}