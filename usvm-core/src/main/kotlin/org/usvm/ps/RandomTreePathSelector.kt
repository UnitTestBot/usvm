package org.usvm.ps

import org.usvm.PathsTrieNode
import org.usvm.UPathSelector
import org.usvm.UState
import java.util.IdentityHashMap

/**
 * [UPathSelector] implementation which selects the next state by descending
 * from root to leaf in symbolic execution tree. The child on each step is selected randomly.
 * (see KLEE's random path search heuristic)
 *
 * This path selector is guaranteed to peek only and all states which were added via [add], even
 * if tree statistics contains some other states or doesn't contain some added states. To achieve this,
 * a separate from path tree collection of states is maintained, and to avoid revisiting the nodes in tree, and to avoid revisiting the nodes in tree
 * which states are not in our collection, an [ignoreToken] added to such nodes. Nodes with [ignoreToken] are not
 * visited on further traversals.
 *
 * @param root a root node for a symbolic execution tree.
 * @param randomNonNegativeInt function returning non negative random integer used to select the next child in tree.
 * @param ignoreToken token to visit only the subtree of not removed states. Should be different for different consumers.
 */
internal class RandomTreePathSelector<State : UState<*, *, *, Statement, *, State>, Statement>(
    private val root: PathsTrieNode<State, Statement>,
    private val randomNonNegativeInt: () -> Int,
    private val ignoreToken: Long = 0,
) : UPathSelector<State> {

    /**
     * A set of states that are registered in this path selector.
     */
    private val states = hashSetOf<State>()

    /**
     * A map of nodes useful for this particular selector.
     * It is required to separate nodes that were not used by this path selector in parallel mode.
     */
    private val visitedNodes = IdentityHashMap<PathsTrieNode<State, Statement>, Boolean>()

    init {
        visitedNodes[root] = true
    }

    override fun isEmpty(): Boolean {
        return states.isEmpty()
    }

    private tailrec fun peekRec(): State {
        var currentNode: PathsTrieNode<State, Statement> = root
        var peekedState: State? = null

        // Trying to select a state by descending to tree leaves
        while (true) {
            // All nodes of the tree contain our ignore token, so peeked state is null
            if (currentNode.labels.contains(ignoreToken)) {
                break
            }

            // Leaf if reached
            // Note that we may have several nodes satisfying the predicate here since
            // they might be created because of type forks or approximation forks.
            // In such case, they have the same path but different path constraints.
            val nodeFromThisSelector = currentNode.states.firstOrNull { it in states }
            if (nodeFromThisSelector != null) {
                peekedState = nodeFromThisSelector
                break
            }

            // Take only children without ignoreTokens and from this path selector
            val children = currentNode
                .children
                .values
                .filter { !it.labels.contains(ignoreToken) && it in visitedNodes }

            if (children.isEmpty()) {
                ignoreSubtree(currentNode)

                // We don't have to remove any states from this state since all of them
                // do not belong to the current path selector.

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

        // Peeked a state from the tree, but it's not in our states.
        // Exclude the node from further search and try again.
        ignoreSubtree(currentNode)

        return peekRec()
    }

    private fun ignoreSubtree(currentNode: PathsTrieNode<State, Statement>) {
        currentNode.addLabel(ignoreToken)
        states -= currentNode.states
    }

    override fun peek(): State {
        return peekRec()
    }

    override fun update(state: State) {
        registerLocation(state.pathLocation)
    }

    internal fun registerLocation(pathLocation: PathsTrieNode<State, Statement>) {
        visitedNodes[pathLocation] = true
    }

    override fun add(states: Collection<State>) {
        this.states.addAll(states)
        states.forEach { visitedNodes[it.pathLocation] = true }
    }

    override fun remove(state: State) {
        states.remove(state)
    }
}
