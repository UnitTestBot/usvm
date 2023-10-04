package org.usvm.ps

import org.usvm.PathNode
import org.usvm.UPathSelector
import org.usvm.algorithms.TrieNode
import org.usvm.algorithms.TrieNode.Companion.root

private data class TreeNodeData<State, Location>(
    val states: MutableSet<State>,
    val pathNode: PathNode<Location>,
)

private typealias TreeNode<State, Location> = TrieNode<Location, TreeNodeData<State, Location>>

class ExecutionTreeTracker<State, Location>(
    pathRootNode: PathNode<Location>,
    private val stateToPathNode: (State) -> PathNode<Location>,
) : UPathSelector<State> {

    // TODO: I don't really like how it looks
    private val rootNode: TreeNode<State, Location> = root { TreeNodeData(mutableSetOf(), pathRootNode) }
    private val stateToLastNode: MutableMap<State, TreeNode<State, Location>> = mutableMapOf()
    private val pathNodeToNode: MutableMap<PathNode<Location>, TreeNode<State, Location>> = mutableMapOf()

    init {
        pathNodeToNode[pathRootNode] = rootNode
    }

    override fun isEmpty(): Boolean = rootNode.children.isEmpty() && rootNode.value.states.isEmpty()
    override fun peek(): State {
        var cur = rootNode
        while (cur.value.states.isEmpty()) {
            cur = cur.children.values.first()
        }
        return cur.value.states.first()
    }

    private fun cleanUp(node: TreeNode<State, Location>) {
        var cur = node
        while (cur != rootNode && cur.children.isEmpty() && cur.value.states.isEmpty()) {
            pathNodeToNode.remove(cur.value.pathNode)
            cur = cur.drop() ?: return
        }
    }

    override fun remove(state: State) {
        val treeNode = stateToLastNode.remove(state) ?: return
        treeNode.value.states.remove(state)
        cleanUp(treeNode)
    }

    override fun update(state: State) {
        val node = stateToLastNode.remove(state)?.apply { value.states -= state }
        addState(state)
        if (node != null) {
            cleanUp(node)
        }
    }

    private fun ensurePathNodeTracked(topNode: PathNode<Location>): TreeNode<State, Location> {
        var pathNode = topNode
        val pathNodesToAdd = mutableListOf<PathNode<Location>>()
        while (pathNode !in pathNodeToNode) {
            pathNodesToAdd += pathNode
            pathNode = requireNotNull(pathNode.parent)
        }
        var treeNode = pathNodeToNode.getValue(pathNode)
        for (pathNodeToAdd in pathNodesToAdd.asReversed()) {
            treeNode = treeNode.add(pathNodeToAdd.statement) { TreeNodeData(mutableSetOf(), pathNodeToAdd) }
            pathNodeToNode[pathNodeToAdd] = treeNode
        }
        return treeNode
    }

    private fun addState(state: State) {
        require(state !in stateToLastNode) { "State already in the execution tree" }
        val treeNode = ensurePathNodeTracked(stateToPathNode(state))
        stateToLastNode[state] = treeNode
        treeNode.value.states += state
    }

    override fun add(states: Collection<State>) {
        states.forEach(::addState)
    }

    fun rootNode() = rootNode.value.pathNode

    fun childrenOf(pathNode: PathNode<Location>): List<PathNode<Location>> =
        pathNodeToNode[pathNode]?.children?.values?.map { it.value.pathNode } ?: emptyList()

    fun statesAt(pathNode: PathNode<Location>): Collection<State> =
        pathNodeToNode[pathNode]?.value?.states ?: emptyList()

    fun representative(pathNode: PathNode<Location>): PathNode<Location>? = pathNodeToNode[pathNode]?.value?.pathNode
}
