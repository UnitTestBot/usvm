package org.usvm.machine.ps.strategies.impls

import org.usvm.UPathSelector
import org.usvm.language.PyInstruction
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.*
import kotlin.random.Random

fun makeDelayedForkByInstructionActionStrategy(
    random: Random
): WeightedActionStrategy<BaselineDelayedForkState, DelayedForkByInstructionGraph> =
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
): Action<BaselineDelayedForkState, DelayedForkByInstructionGraph>(weight) {
    protected fun findAvailableInstructions(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex<BaselineDelayedForkState>) -> Boolean,
    ): List<PyInstruction> =
        graph.nodesByInstruction.keys.filter { instruction ->
            val nodes = graph.nodesByInstruction[instruction]!!
            nodes.any { it in graph.aliveNodesAtDistanceOne && isAvailable(it) }
        }

    protected fun chooseDelayedFork(
        graph: DelayedForkByInstructionGraph,
        isAvailable: (DelayedForkGraphInnerVertex<BaselineDelayedForkState>) -> Boolean,
        random: Random
    ): DelayedForkGraphInnerVertex<BaselineDelayedForkState> {
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
    private val predicate = { node: DelayedForkGraphInnerVertex<BaselineDelayedForkState> ->
        node.delayedForkState.successfulTypes.isEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkByInstructionGraph): Boolean =
        findAvailableInstructions(graph, predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkByInstructionGraph,
        random: Random
    ): PyPathSelectorAction<BaselineDelayedForkState> =
        MakeDelayedFork(chooseDelayedFork(graph, predicate, random))

    override fun toString(): String = "ServeNewDelayedForkByInstruction"
}

object ServeOldDelayedForkByInstruction: DelayedForkByInstructionAction(ServeOldDelayedFork.weight) {
    private val predicate = { node: DelayedForkGraphInnerVertex<BaselineDelayedForkState> ->
        node.delayedForkState.successfulTypes.isNotEmpty() && node.delayedForkState.size > 0
    }

    override fun isAvailable(graph: DelayedForkByInstructionGraph): Boolean =
        findAvailableInstructions(graph, predicate).isNotEmpty()

    override fun makeAction(
        graph: DelayedForkByInstructionGraph,
        random: Random
    ): PyPathSelectorAction<BaselineDelayedForkState> =
        MakeDelayedFork(chooseDelayedFork(graph, predicate, random))

    override fun toString(): String = "ServeOldDelayedForkByInstruction"
}

class DelayedForkByInstructionGraph(
    basePathSelectorCreation: () -> UPathSelector<PyState>,
    root: DelayedForkGraphRootVertex<BaselineDelayedForkState>
): BaselineDelayedForkGraph(basePathSelectorCreation, root) {
    internal val nodesByInstruction = mutableMapOf<PyInstruction, MutableSet<DelayedForkGraphInnerVertex<BaselineDelayedForkState>>>()

    override fun addVertex(df: DelayedFork, vertex: DelayedForkGraphInnerVertex<BaselineDelayedForkState>) {
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
): DelayedForkGraphCreation<BaselineDelayedForkState, DelayedForkByInstructionGraph> {
    override fun createEmptyDelayedForkState(): BaselineDelayedForkState =
        BaselineDelayedForkState()

    override fun createOneVertexGraph(root: DelayedForkGraphRootVertex<BaselineDelayedForkState>): DelayedForkByInstructionGraph =
        DelayedForkByInstructionGraph(basePathSelectorCreation, root)
}