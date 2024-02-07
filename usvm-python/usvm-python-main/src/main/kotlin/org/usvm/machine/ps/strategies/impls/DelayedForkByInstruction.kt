package org.usvm.machine.ps.strategies.impls

import org.usvm.UPathSelector
import org.usvm.language.PyInstruction
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.*
import kotlin.random.Random

fun makeDelayedForkByInstructionActionStrategy(
    random: Random
): WeightedActionStrategy<DelayedForkState, DelayedForkByInstructionGraph> =
    WeightedActionStrategy(
        random,
        listOf(
            PeekFromRoot,
            ServeNewDelayedForkByInstruction,
            PeekFromStateWithDelayedFork,
            ServeOldDelayedForkByInstruction,
            PeekExecutedStateWithConcreteType
        )
    )

sealed class DelayedForkByInstructionAction(
    weight: Double
): Action<DelayedForkState, DelayedForkByInstructionGraph>(weight) {
    protected fun findAvailableInstructions(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex<DelayedForkState>) -> Boolean,
    ): List<PyInstruction> =
        graph.nodesByInstruction.keys.filter { instruction ->
            val nodes = graph.nodesByInstruction[instruction]!!
            nodes.any { it in graph.aliveNodesAtDistanceOne && isAvailable(it) }
        }

    protected fun chooseDelayedFork(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex<DelayedForkState>) -> Boolean,
        random: Random
    ): DelayedForkGraphInnerVertex<DelayedForkState> {
        val availableInstructions = findAvailableInstructions(graph, isAvailable)
        val size = availableInstructions.size
        require(size > 0)
        val idx = random.nextInt(0, size)
        val nodes = graph.nodesByInstruction[availableInstructions[idx]]!!.filter {
            it in graph.aliveNodesAtDistanceOne && isAvailable(it)
        }
        require(nodes.isNotEmpty())
        return nodes.random(random)
    }
}

object ServeNewDelayedForkByInstruction: DelayedForkByInstructionAction(ServeNewDelayedFork.weight) {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkByInstructionGraph): Boolean =
        findAvailableInstructions(graph, predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkByInstructionGraph,
        random: Random
    ): PyPathSelectorAction<DelayedForkState> =
        MakeDelayedFork(chooseDelayedFork(graph, predicate, random))

    override fun toString(): String = "ServeNewDelayedForkByInstruction"
}

object ServeOldDelayedForkByInstruction: DelayedForkByInstructionAction(ServeOldDelayedFork.weight) {
    private val predicate = { node: DelayedForkGraphInnerVertex<DelayedForkState> ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkByInstructionGraph): Boolean =
        findAvailableInstructions(graph, predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkByInstructionGraph,
        random: Random
    ): PyPathSelectorAction<DelayedForkState> =
        MakeDelayedFork(chooseDelayedFork(graph, predicate, random))

    override fun toString(): String = "ServeOldDelayedForkByInstruction"
}

class DelayedForkByInstructionGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex<DelayedForkState>
): BaselineDelayedForkGraph(basePathSelectorCreation, root) {
    internal val nodesByInstruction = mutableMapOf<PyInstruction, MutableSet<DelayedForkGraphInnerVertex<DelayedForkState>>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<DelayedForkState>) {
        super.addVertex(df, vertex)
        var set = nodesByInstruction[vertex.delayedFork.state.pathNode.statement]
        if (set == null) {
            set = mutableSetOf()
            nodesByInstruction[vertex.delayedFork.state.pathNode.statement] = set
        }
        set.add(vertex)
    }
}

class DelayedForkByInstructionGraphCreation(
    private val basePathSelectorCreation: () -> UPathSelector<PyState>
): DelayedForkGraphCreation<DelayedForkState, DelayedForkByInstructionGraph> {
    override fun createEmptyDelayedForkState(): DelayedForkState =
        DelayedForkState()

    override fun createOneVertexGraph(root: DelayedForkGraphRootVertex<DelayedForkState>): DelayedForkByInstructionGraph =
        DelayedForkByInstructionGraph(basePathSelectorCreation, root)
}