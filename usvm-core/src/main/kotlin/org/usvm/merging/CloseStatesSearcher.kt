package org.usvm.merging

import org.usvm.UState
import org.usvm.algorithms.limitedBfsTraversal
import org.usvm.ps.ExecutionTreeTracker
import org.usvm.statistics.distances.CfgStatistics

interface CloseStatesSearcher<State> {
    fun findCloseStates(state: State): Sequence<State>

    fun update(state: State)
    fun add(states: Collection<State>)

    fun remove(state: State)
}

class CloseStatesSearcherImpl<Method, Statement, State>(
    private val executionTreeTracker: ExecutionTreeTracker<State, Statement>,
    private val statistics: CfgStatistics<Method, Statement>,
    private val maxTrieSearchDistance: UInt = 30u,
    private val maxCfgSearchDistance: UInt = 15u,
    private val maxProcessedNodes: Int = 100,
) : CloseStatesSearcher<State>
    where State : UState<*, Method, Statement, *, *, State> {
    override fun findCloseStates(state: State): Sequence<State> {
        val method = state.lastEnteredMethod
        val pathNode = checkNotNull(executionTreeTracker.representative(state.pathNode))
        val nodes = limitedBfsTraversal(
            listOf(pathNode),
            depthLimit = maxTrieSearchDistance
        ) {
            // TODO: very strange code, let's refactor it
            executionTreeTracker.childrenOf(it)
                .asSequence() + listOfNotNull(it.parent?.let(executionTreeTracker::representative))
        }
            .take(maxProcessedNodes)
            .mapNotNull { otherNode ->
                if (otherNode == pathNode || executionTreeTracker.statesAt(otherNode).isEmpty()) {
                    return@mapNotNull null
                }
                val cfgDistance = statistics.getShortestDistance(
                    method,
                    otherNode.statement,
                    pathNode.statement
                )
                (otherNode to cfgDistance).takeIf { cfgDistance <= maxCfgSearchDistance }
            }
            .sortedBy { it.second }
        return nodes.flatMap { (node, _) -> executionTreeTracker.statesAt(node) }
    }

    override fun update(state: State) {
        executionTreeTracker.update(state)
    }

    override fun add(states: Collection<State>) {
        executionTreeTracker.add(states)
    }

    override fun remove(state: State) {
        executionTreeTracker.remove(state)
    }
}