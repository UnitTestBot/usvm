package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.PathsTreeStatistics

/**
 * [UPathSelector] implementation which selects the next state by descending
 * from root to leaf in symbolic execution tree. The child on each step is selected randomly.
 * (see KLEE's random path search heuristic)
 *
 * This path selector is guaranteed to peek only and all states which were added via [add], even
 * if tree statistics contains some other states or doesn't contain some added states. To achieve this,
 * a separate from path tree collection of states is maintained, and to avoid revisiting the nodes in tree
 * which states are not in our collection, an [ignoreToken] added to such nodes. Nodes with [ignoreToken] are not
 * visited on further traversals.
 * TODO: In fact, there should not be state which was not added to tree, but was added to selector, and vice versa,
 *  so in practice the separate collection can be removed.
 *
 * @param pathsTreeStatistics symbolic execution tree statistics used to peek states from.
 * @param randomNonNegativeInt function returning non negative random integer used to select the next child in tree.
 * @param ignoreToken token to visit only the subtree of not removed states. Should be different for
 * different instances of [PathsTreeStatistics] consumers.
 */
internal class RandomTreePathSelector<State : UState<*, *, *, *>>(
    private val pathsTreeStatistics: PathsTreeStatistics<*, *, State>,
    private val randomNonNegativeInt: () -> Int,
    private val ignoreToken: Long = 0,
) : UPathSelector<State> {

    private val states = HashSet<State>()
    
    override fun isEmpty(): Boolean {
        return states.isEmpty()
    }

    private tailrec fun peekRec(): State {
        var currentNode = pathsTreeStatistics.root
        var peekedState: State? = null

        // Trying to select a state by descending to tree leaves
        while (true) {
            // All nodes of the tree contain our ignore token, so peeked state is null
            if (currentNode.ignoreTokens.contains(ignoreToken)) {
                break
            }

            // Leaf if reached
            if (currentNode.state != null) {
                peekedState = currentNode.state
                break
            }

            val children = currentNode.children.filterNot { it.ignoreTokens.contains(ignoreToken) }
            if (children.isEmpty()) {
                currentNode.addIgnoreToken(ignoreToken)
                val parent = currentNode.parent ?: break
                currentNode = parent
                // All children are excluded from search, exclude the parent from the
                // search as well and backtrack
                continue
            }

            // Select the next node to visit from not ignored children
            val randomValue = randomNonNegativeInt()
            check(randomValue >= 0) { "randomNonNegativeInt() returned a negative value" }
            currentNode = children[randomValue % children.size]
        }

        peekedState
            // If we can't peek a state from tree, try to fallback on our states collection
            ?: return states.firstOrNull() ?: throw NoSuchElementException("RandomTreePathSelector is empty")

        if (peekedState in states) {
            return peekedState
        }

        // Peeked a state from tree, but it's not in our states. Exclude the node from further search
        // and try again
        currentNode.addIgnoreToken(ignoreToken)
        return peekRec()
    }

    override fun peek(): State {
        return peekRec()
    }

    override fun update(state: State) { }

    override fun add(states: Collection<State>) {
        this.states.addAll(states)
    }

    override fun remove(state: State) {
        states.remove(state)
    }
}
