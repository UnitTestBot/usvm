package org.usvm.statistics

import org.usvm.UState
import java.util.concurrent.ConcurrentHashMap

/**
 * Symbolic execution tree node.
 *
 * @see [PathsTreeStatistics]
 */
interface PathsTreeNode<State> {
    /**
     * Forked states' nodes.
     */
    val children: Collection<PathsTreeNode<State>>

    /**
     * State which is located in this node. Null for non-leaf nodes.
     */
    val state: State?

    /**
     * The node which has this node as a child.
     */
    val parent: PathsTreeNode<State>?

    /**
     * Labels which can be used to exclude some tree branches from traversal.
     */
    val ignoreTokens: Set<Long>

    /**
     * Node's depth in the tree. 0 for root node, 1 for its children etc.
     */
    val depth: Int

    /**
     * Adds a new token to [ignoreTokens] collection.
     */
    fun addIgnoreToken(token: Long)
}

private class PathsTreeNodeImpl<State> (
    override val depth: Int,
    val childrenImpl: MutableSet<PathsTreeNodeImpl<State>>,
    override val parent: PathsTreeNodeImpl<State>?,
    var stateValue: State?
) : PathsTreeNode<State> {

    val locker = Any()
    override val ignoreTokens = HashSet<Long>()

    override val children: Collection<PathsTreeNode<State>>
        get() = synchronized(locker) { childrenImpl }

    override val state: State?
        get() = synchronized(locker) { stateValue }

    override fun addIgnoreToken(token: Long) {
        synchronized(locker) {
            ignoreTokens.add(token)
        }
    }
}

/**
 * [UMachineObserver] implementation which maintains the symbolic execution tree of process. Each leaf node
 * contains a reference on the state. When this state is forked, a new leaf node is created for each fork (including
 * the forked state itself) and these nodes are added as children to the former leaf node.
 *
 * Operations are thread-safe.
 *
 * @param initialState root state.
 */
class PathsTreeStatistics<Method, Statement, State : UState<*, *, Method, Statement>>(initialState: State) : UMachineObserver<State> {

    private val stateIdToLeaf = ConcurrentHashMap<UInt, PathsTreeNodeImpl<State>>()

    val root: PathsTreeNode<State>

    init {
        val initialStateLeaf = PathsTreeNodeImpl(0, HashSet(), null, initialState)
        root = initialStateLeaf
        stateIdToLeaf[initialState.id] = initialStateLeaf
    }

    fun getStateDepth(state: State): Int {
        return stateIdToLeaf[state.id]?.depth ?: throw IllegalArgumentException("Trying to get depth of state not recorded in path tree statistics")
    }

    override fun onState(
        parent: State,
        forks: Sequence<State>
    ) {
        // Notice: empty forks doesn't exactly mean that there was no fork at all
        if (!forks.any()) {
            return
        }

        val parentNode = stateIdToLeaf[parent.id] ?: throw IllegalArgumentException("Trying to track fork of state not recorded in path tree statistics")
        require(forks.all { !stateIdToLeaf.containsKey(it.id) }) { "One of the forks is already recorded in paths tree statistics" }
        synchronized(parentNode.locker) {
            if (parentNode != stateIdToLeaf.getValue(parent.id)) {
                return
            }
            val depth = parentNode.depth + 1
            val newParentLeaf = PathsTreeNodeImpl(depth, HashSet(), parentNode, parentNode.stateValue)
            parentNode.childrenImpl.add(newParentLeaf)
            parentNode.stateValue = null
            forks.forEach {
                val forkLeaf = PathsTreeNodeImpl(depth, HashSet(), parentNode, it)
                stateIdToLeaf[it.id] = forkLeaf
                parentNode.childrenImpl.add(forkLeaf)
            }
            stateIdToLeaf[parent.id] = newParentLeaf
        }
    }
}
