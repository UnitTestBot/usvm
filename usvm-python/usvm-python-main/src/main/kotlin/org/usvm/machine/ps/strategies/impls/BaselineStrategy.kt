package org.usvm.machine.ps.strategies.impls

import mu.KLogging
import org.usvm.UPathSelector
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkGraphCreation
import org.usvm.machine.ps.strategies.DelayedForkGraphInnerVertex
import org.usvm.machine.ps.strategies.DelayedForkGraphRootVertex
import org.usvm.machine.ps.strategies.DelayedForkGraphVertex
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.DelayedForkStrategy
import org.usvm.machine.ps.strategies.MakeDelayedFork
import org.usvm.machine.ps.strategies.Peek
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import org.usvm.machine.ps.strategies.TypeRating
import kotlin.random.Random

// For now, these values were chosen in an arbitrary way.
// TODO: find best possible values
const val PROB_0 = 1.0
const val PROB_1 = 0.6
const val PROB_2 = 0.875
const val PROB_3 = 0.8
const val INF = 100.0

val baselineProbabilities = listOf(PROB_0, PROB_1, PROB_2, PROB_3, 1.0)
private val probNegations = baselineProbabilities
    .drop(1)
    .runningFold(1.0) { acc, p -> acc * (1 - p) }
val baselineWeights = // listOf(100.0, 0.6, 0.35, 0.04, 0.01)
    listOf(INF) + (baselineProbabilities.drop(1) zip probNegations.dropLast(1)).map { (a, b) -> a * b }

fun makeBaselinePriorityActionStrategy(
    random: Random,
): RandomizedPriorityActionStrategy<DelayedForkState, BaselineDelayedForkGraph> =
    RandomizedPriorityActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedFork,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedFork
        ),
        baselineProbabilities
    )

fun makeBaselineWeightedActionStrategy(
    random: Random,
): WeightedActionStrategy<DelayedForkState, BaselineDelayedForkGraph> =
    WeightedActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedFork,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedFork
        ),
        baselineWeights
    )

sealed class BaselineAction : Action<DelayedForkState, BaselineDelayedForkGraph> {
    protected fun chooseAvailableVertex(
        available: List<DelayedForkGraphInnerVertex<DelayedForkState>>,
        random: Random,
    ): DelayedForkGraphInnerVertex<DelayedForkState> {
        require(available.isNotEmpty())
        val idx = random.nextInt(0, available.size)
        return available[idx]
    }
}

object PeekExecutedStateWithConcreteType : BaselineAction() {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorForExecutedStatesWithConcreteTypes.isEmpty()

    override fun makeAction(
        graph: BaselineDelayedForkGraph,
        random: Random,
    ): PyPathSelectorAction<DelayedForkState> =
        Peek(graph.pathSelectorForExecutedStatesWithConcreteTypes)
}

object PeekFromRoot : BaselineAction() {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorWithoutDelayedForks.isEmpty()

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> =
        Peek(graph.pathSelectorWithoutDelayedForks)

    override fun toString(): String = "PeekFromRoot"
}

object ServeNewDelayedFork : BaselineAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        graph.aliveNodesAtDistanceOne.any(predicate)

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> {
        val available = graph.aliveNodesAtDistanceOne.filter(predicate)
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeNewDelayedFork"
}

object PeekFromStateWithDelayedFork : BaselineAction() {
    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        !graph.pathSelectorWithDelayedForks.isEmpty()

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> {
        return Peek(graph.pathSelectorWithDelayedForks)
    }

    override fun toString(): String = "PeekFromStateWithDelayedFork"
}

object ServeOldDelayedFork : BaselineAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: BaselineDelayedForkGraph): Boolean =
        graph.aliveNodesAtDistanceOne.any(predicate)

    override fun makeAction(graph: BaselineDelayedForkGraph, random: Random): PyPathSelectorAction<DelayedForkState> {
        val available = graph.aliveNodesAtDistanceOne.filter(predicate)
        return MakeDelayedFork(chooseAvailableVertex(available, random))
    }

    override fun toString(): String = "ServeOldDelayedFork"
}

class BaselineDelayedForkStrategy : DelayedForkStrategy<DelayedForkState> {
    private var lastIdx = -1
    override fun chooseTypeRating(state: DelayedForkState): TypeRating {
        require(state.size > 0) {
            "Cannot choose type rating from empty set"
        }
        lastIdx = (lastIdx + 1) % state.size
        val idx = lastIdx
        return state.getAt(idx)
    }
}

class BaselineDFGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>,
) : DelayedForkGraphCreation<DelayedForkState, BaselineDelayedForkGraph> {
    override fun createEmptyDelayedForkState(): DelayedForkState =
        DelayedForkState()

    override fun createOneVertexGraph(root: DelayedForkGraphRootVertex<DelayedForkState>): BaselineDelayedForkGraph =
        BaselineDelayedForkGraph(basePathSelectorCreation, root)
}

open class BaselineDelayedForkGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex<DelayedForkState>,
) : DelayedForkGraph<DelayedForkState>(root) {

    internal val pathSelectorWithoutDelayedForks = basePathSelectorCreation()
    internal val pathSelectorWithDelayedForks = basePathSelectorCreation()
    internal val pathSelectorForExecutedStatesWithConcreteTypes = basePathSelectorCreation()
    internal val aliveNodesAtDistanceOne = mutableSetOf<DelayedForkGraphInnerVertex<DelayedForkState>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<DelayedForkState>) {
        super.addVertex(df, vertex)
        if (vertex.parent == root) {
            logger.debug("Adding node to aliveNodesAtDistanceOne")
            aliveNodesAtDistanceOne.add(vertex)
        }
    }

    override fun updateVertex(vertex: DelayedForkGraphInnerVertex<DelayedForkState>) {
        if (vertex.delayedForkState.isDead) {
            aliveNodesAtDistanceOne.remove(vertex)
        }
    }

    override fun addExecutedStateWithConcreteTypes(state: PyState) {
        pathSelectorForExecutedStatesWithConcreteTypes.add(listOf(state))
    }

    override fun addStateToVertex(vertex: DelayedForkGraphVertex<DelayedForkState>, state: PyState) {
        when (vertex) {
            is DelayedForkGraphRootVertex -> {
                pathSelectorWithoutDelayedForks.add(listOf(state))
            }
            is DelayedForkGraphInnerVertex -> {
                pathSelectorWithDelayedForks.add(listOf(state))
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
