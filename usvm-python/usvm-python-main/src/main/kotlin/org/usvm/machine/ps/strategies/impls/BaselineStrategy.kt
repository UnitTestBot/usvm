package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.*
import kotlin.random.Random

fun makeBaselineActionStrategy(
    random: Random
): WeightedActionStrategy<BaselineDelayedForkState, BaselineDelayedForkGraph> =
    WeightedActionStrategy(
        random,
        listOf(
            PeekFromRoot,
            ServeNewDelayedFork,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedFork,
            PeekExecutedStateWithConcreteType
        )
    )

sealed class BaselineAction(
    weight: Double
): Action<BaselineDelayedForkState, BaselineDelayedForkGraph>(weight) {
    protected fun chooseAvailableVertex(
        available: List<DelayedForkGraphInnerVertex<BaselineDelayedForkState>>,
        random: Random
    ): DelayedForkGraphInnerVertex<BaselineDelayedForkState> {
        require(available.isNotEmpty())
        val idx = random.nextInt(0, available.size)
        return available[idx]
    }
}

object PeekFromRoot: BaselineAction(0.6) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorWithoutDelayedForks.isEmpty()

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<BaselineDelayedForkState> =
        Peek(graph.pathSelectorWithoutDelayedForks)

    override fun toString(): String = "PeekFromRoot"
}

object ServeNewDelayedFork: BaselineAction(0.3) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        graph.aliveNodesAtDistanceOne.any { it.delayedForkState.successfulTypes.isEmpty() && it.delayedForkState.size > 0 }

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<BaselineDelayedForkState> {
        val available = graph.aliveNodesAtDistanceOne.filter { it.delayedForkState.successfulTypes.isEmpty() }
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeNewDelayedFork"
}

object PeekFromStateWithDelayedFork: BaselineAction(0.088) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorWithDelayedForks.isEmpty()

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<BaselineDelayedForkState> {
        return Peek(graph.pathSelectorWithDelayedForks)
    }

    override fun toString(): String = "PeekFromStateWithDelayedFork"
}

object ServeOldDelayedFork: BaselineAction(0.012) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        graph.aliveNodesAtDistanceOne.any { it.delayedForkState.successfulTypes.isNotEmpty() && it.delayedForkState.size > 0 }

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<BaselineDelayedForkState> {
        val available = graph.aliveNodesAtDistanceOne.filter { it.delayedForkState.successfulTypes.isNotEmpty() }
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeOldDelayedFork"
}

object PeekExecutedStateWithConcreteType: BaselineAction(100.0) {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorForExecutedStatesWithConcreteTypes.isEmpty()

    override fun makeAction(
        graph: BaselineDelayedForkGraph,
        random: Random
    ): PyPathSelectorAction<BaselineDelayedForkState> =
        Peek(graph.pathSelectorForExecutedStatesWithConcreteTypes)

}

object BaselineDelayedForkStrategy: DelayedForkStrategy<BaselineDelayedForkState> {
    override fun chooseTypeRating(state: BaselineDelayedForkState): TypeRating {
        require(state.size > 0) {
            "Cannot choose type rating from empty set"
        }
        val idx = state.nextIdx
        state.nextIdx = (state.nextIdx + 1) % state.size
        return state.getAt(idx)
    }
}

class BaselineDFGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>
): DelayedForkGraphCreation<BaselineDelayedForkState, BaselineDelayedForkGraph> {
    override fun createEmptyDelayedForkState(): BaselineDelayedForkState =
        BaselineDelayedForkState()

    override fun createOneVertexGraph(root: DelayedForkGraphRootVertex<BaselineDelayedForkState>): BaselineDelayedForkGraph =
        BaselineDelayedForkGraph(basePathSelectorCreation, root)
}

class BaselineDelayedForkState: DelayedForkState() {
    internal var nextIdx = 0
}

open class BaselineDelayedForkGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex<BaselineDelayedForkState>
): DelayedForkGraph<BaselineDelayedForkState>(root) {

    internal val pathSelectorWithoutDelayedForks = basePathSelectorCreation()
    internal val pathSelectorWithDelayedForks = basePathSelectorCreation()
    internal val pathSelectorForExecutedStatesWithConcreteTypes = basePathSelectorCreation()
    internal val aliveNodesAtDistanceOne = mutableSetOf<DelayedForkGraphInnerVertex<BaselineDelayedForkState>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<BaselineDelayedForkState>) {
        super.addVertex(df, vertex)
        if (vertex.parent == root) {
            logger.debug("Adding node to aliveNodesAtDistanceOne")
            aliveNodesAtDistanceOne.add(vertex)
        }
    }

    override fun updateVertex(vertex: DelayedForkGraphInnerVertex<BaselineDelayedForkState>) {
        if (vertex.delayedForkState.isDead)
            aliveNodesAtDistanceOne.remove(vertex)
    }

    override fun addExecutedStateWithConcreteTypes(state: PyState) {
        pathSelectorForExecutedStatesWithConcreteTypes.add(listOf(state))
    }

    override fun addStateToVertex(vertex: DelayedForkGraphVertex<BaselineDelayedForkState>, state: PyState) {
        when (vertex) {
            is DelayedForkGraphRootVertex -> pathSelectorWithoutDelayedForks.add(listOf(state))
            is DelayedForkGraphInnerVertex -> {
                pathSelectorWithDelayedForks.add(listOf(state))
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}