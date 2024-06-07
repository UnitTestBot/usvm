package org.usvm.machine.ps.strategies.impls

import org.usvm.UPathSelector
import org.usvm.language.PyInstruction
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.DelayedForkGraphCreation
import org.usvm.machine.ps.strategies.DelayedForkGraphInnerVertex
import org.usvm.machine.ps.strategies.DelayedForkGraphRootVertex
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.MakeDelayedFork
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import kotlin.random.Random

fun makeDelayedForkByInstructionPriorityStrategy(
    random: Random,
): RandomizedPriorityActionStrategy<DelayedForkState, DelayedForkByInstructionGraph> =
    RandomizedPriorityActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedForkByInstruction,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedForkByInstruction
        ),
        baselineProbabilities
    )

fun makeDelayedForkByInstructionWeightedStrategy(
    random: Random,
): WeightedActionStrategy<DelayedForkState, DelayedForkByInstructionGraph> =
    WeightedActionStrategy(
        random,
        listOf(
            PeekExecutedStateWithConcreteType,
            PeekFromRoot,
            ServeNewDelayedForkByInstruction,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedForkByInstruction
        ),
        baselineWeights
    )

sealed class DelayedForkByInstructionAction : Action<DelayedForkState, DelayedForkByInstructionGraph> {
    protected fun findAvailableInstructions(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex<DelayedForkState>) -> Boolean,
    ): List<PyInstruction> =
        graph.nodesByInstruction.entries.filter { (_, nodes) ->
            nodes.any { it in graph.aliveNodesAtDistanceOne && isAvailable(it) }
        }.map {
            it.key
        }

    protected fun chooseDelayedFork(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex<DelayedForkState>) -> Boolean,
        random: Random,
    ): DelayedForkGraphInnerVertex<DelayedForkState> {
        val availableInstructions = findAvailableInstructions(graph, isAvailable)
        val size = availableInstructions.size
        require(size > 0)
        val idx = random.nextInt(0, size)
        val rawNodes = graph.nodesByInstruction[availableInstructions[idx]]
            ?: error("${availableInstructions[idx]} not in map graph.nodesByInstruction")
        val nodes = rawNodes.filter {
            it in graph.aliveNodesAtDistanceOne && isAvailable(it)
        }
        require(nodes.isNotEmpty())
        return nodes.random(random)
    }
}

data object ServeNewDelayedForkByInstruction : DelayedForkByInstructionAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkByInstructionGraph): Boolean =
        findAvailableInstructions(graph, predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkByInstructionGraph,
        random: Random,
    ): PyPathSelectorAction<DelayedForkState> =
        MakeDelayedFork(chooseDelayedFork(graph, predicate, random))
}

data object ServeOldDelayedForkByInstruction : DelayedForkByInstructionAction() {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkByInstructionGraph): Boolean =
        findAvailableInstructions(graph, predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkByInstructionGraph,
        random: Random,
    ): PyPathSelectorAction<DelayedForkState> =
        MakeDelayedFork(chooseDelayedFork(graph, predicate, random))
}

class DelayedForkByInstructionGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex<DelayedForkState>,
) : BaselineDelayedForkGraph(basePathSelectorCreation, root) {
    internal val nodesByInstruction =
        mutableMapOf<PyInstruction, MutableSet<DelayedForkGraphInnerVertex<DelayedForkState>>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<DelayedForkState>) {
        super.addVertex(df, vertex)
        val set = nodesByInstruction[vertex.delayedFork.state.pathNode.statement]
            ?: mutableSetOf<DelayedForkGraphInnerVertex<DelayedForkState>>().also {
                nodesByInstruction[vertex.delayedFork.state.pathNode.statement] = it
            }
        set.add(vertex)
    }
}

class DelayedForkByInstructionGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>,
) : DelayedForkGraphCreation<DelayedForkState, DelayedForkByInstructionGraph> {
    override fun createEmptyDelayedForkState(): DelayedForkState =
        DelayedForkState()

    override fun createOneVertexGraph(
        root: DelayedForkGraphRootVertex<DelayedForkState>,
    ): DelayedForkByInstructionGraph =
        DelayedForkByInstructionGraph(basePathSelectorCreation, root)
}
