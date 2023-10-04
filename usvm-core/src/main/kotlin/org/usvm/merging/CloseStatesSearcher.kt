package org.usvm.merging

import org.usvm.PathNode
import org.usvm.algorithms.limitedBfsTraversal
import org.usvm.ps.ExecutionTreeTracker
import org.usvm.statistics.distances.CfgStatistics

interface CloseStatesSearcher<State> {
    fun findCloseStates(state: State): Sequence<State>
}

class CloseStatesSearcherImpl<Method, Statement, State>(
    private val executionTreeTracker: ExecutionTreeTracker<State, Statement>,
    private val stateToPathNode: (State) -> PathNode<Statement>, // TODO: maybe somehow fix it
    private val stateToMethod: (State) -> Method, // TODO: maybe somehow fix it
    private val statistics: CfgStatistics<Method, Statement>,
    private val maxTrieSearchDistance: UInt = 30u,
    private val maxCfgSearchDistance: UInt = 15u,
    private val maxProcessedNodes: Int = 100,
) : CloseStatesSearcher<State> {
    override fun findCloseStates(state: State): Sequence<State> {
        val method = stateToMethod(state)
        val pathNode = checkNotNull(executionTreeTracker.representative(stateToPathNode(state)))
        val nodes = limitedBfsTraversal(
            listOf(pathNode),
            depthLimit = maxTrieSearchDistance
        ) {
            // TODO: very strange code, let's refactor it
            executionTreeTracker.childrenOf(it)
                .asSequence() + listOfNotNull(it.parent?.let(executionTreeTracker::representative))
        }
            .take(maxProcessedNodes)
            .filter { otherNode ->
                if (otherNode == pathNode || executionTreeTracker.statesAt(otherNode).isEmpty()) {
                    return@filter false
                }
                val cfgDistance = statistics.getShortestDistance(
                    method,
                    otherNode.statement,
                    pathNode.statement
                )
                cfgDistance <= maxCfgSearchDistance
            }
        return nodes.flatMap(executionTreeTracker::statesAt)
    }
}