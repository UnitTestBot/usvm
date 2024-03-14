package org.usvm.ps

import org.usvm.PathNode
import org.usvm.UPathSelector
import org.usvm.UState

/**
 * [UPathSelector] implementation which selects the next state by descending
 * from root to leaf in symbolic execution tree. The child on each step is selected randomly.
 * (see KLEE's random path search heuristic)
 *
 * @param executionTreeTracker a root node for a symbolic execution tree.
 * @param randomNonNegativeInt function returning non negative random integer used to select the next child in tree.
 */
class RandomTreePathSelector<State : UState<*, *, Statement, *, *, State>, Statement>(
    private val executionTreeTracker: ExecutionTreeTracker<State, Statement>,
    private val randomNonNegativeInt: (Int) -> Int,
) : UPathSelector<State> {
    override fun isEmpty(): Boolean = executionTreeTracker.isEmpty()

    override fun peek(): State {
        if (isEmpty()) {
            throw NoSuchElementException()
        }
        var currentNode = executionTreeTracker.rootNode()
        val peekedState: State
        while (true) {
            val nodeFromThisSelector = executionTreeTracker.statesAt(currentNode).firstOrNull()
            if (nodeFromThisSelector != null) {
                peekedState = nodeFromThisSelector
                break
            }

            val children = executionTreeTracker.childrenOf(currentNode) // not empty

            // Select the next node to visit from not ignored children
            val randomValue = randomNonNegativeInt(children.size)
            check(randomValue >= 0) { "randomNonNegativeInt() returned a negative value" }
            currentNode = children[randomValue]
        }
        return peekedState
    }

    override fun update(state: State) = executionTreeTracker.update(state)

    override fun add(states: Collection<State>) = executionTreeTracker.add(states)

    override fun remove(state: State) = executionTreeTracker.remove(state)

    companion object {
        fun <State : UState<*, *, Statement, *, *, State>, Statement> fromRoot(
            root: PathNode<Statement>,
            randomNonNegativeInt: (Int) -> Int,
        ): RandomTreePathSelector<State, Statement> {
            val executionTreeTracker = ExecutionTreeTracker<State, Statement>(root)
            return RandomTreePathSelector(executionTreeTracker, randomNonNegativeInt)
        }
    }
}
