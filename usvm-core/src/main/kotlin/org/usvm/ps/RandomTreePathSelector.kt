package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.PathsTreeStatistics

class RandomTreePathSelector<State : UState<*, *, *, *>>(
    private val pathsTreeStatistics: PathsTreeStatistics<*, *, State>,
    private val ignoreToken: Int,
    private val randomNonNegativeInt: () -> Int
) : UPathSelector<State> {

    private val states = HashSet<State>()
    
    override fun isEmpty(): Boolean {
        return states.isEmpty()
    }

    private tailrec fun peekRec(): State {
        var currentNode = pathsTreeStatistics.root
        var peekedState: State? = null

        while (true) {
            if (currentNode.ignoreTokens.contains(ignoreToken)) {
                break
            }

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
