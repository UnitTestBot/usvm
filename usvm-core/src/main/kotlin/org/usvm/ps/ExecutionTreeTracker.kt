package org.usvm.ps

import org.usvm.PathNode
import org.usvm.UState
import org.usvm.algorithms.TrieNode
import org.usvm.algorithms.TrieNode.Companion.root
import java.util.IdentityHashMap

private data class TreeNodeData<State, Statement>(
    val states: MutableSet<State>,
    val pathNode: PathNode<Statement>,
)

private typealias TreeNode<State, Statement> = TrieNode<Statement, TreeNodeData<State, Statement>>

class ExecutionTreeTracker<State : UState<*, *, Statement, *, *, State>, Statement>(
    pathRootNode: PathNode<Statement>,
) {

    // TODO: I don't really like how it looks
    private val rootNode: TreeNode<State, Statement> = root { TreeNodeData(mutableSetOf(), pathRootNode) }
    private val stateToLastNode: MutableMap<State, TreeNode<State, Statement>> = IdentityHashMap()
    private val pathNodeToNode: MutableMap<PathNode<Statement>, TreeNode<State, Statement>> = IdentityHashMap()

    init {
        pathNodeToNode[pathRootNode] = rootNode
    }

    fun isEmpty(): Boolean = rootNode.children.isEmpty() && rootNode.value.states.isEmpty()
    fun peek(): State {
        var cur = rootNode
        while (cur.value.states.isEmpty()) {
            // because of [cleanUp], nodes without states must be removed, so if a node is still in the tree,
            // there is at least one state in its subtree
            cur = cur.children.values.first()
        }
        return cur.value.states.first()
    }

    private fun cleanUp(node: TreeNode<State, Statement>) {
        var cur = node
        while (cur != rootNode && cur.children.isEmpty() && cur.value.states.isEmpty()) {
            pathNodeToNode.remove(cur.value.pathNode)
            cur = cur.drop() ?: return
        }
    }

    fun remove(state: State) {
        val treeNode = stateToLastNode.remove(state) ?: return
        treeNode.value.states.remove(state)
        cleanUp(treeNode)
    }

    fun update(state: State) {
        val node = stateToLastNode.remove(state)?.apply { value.states -= state }
        addState(state)
        if (node != null) {
            cleanUp(node)
        }
    }

    private fun ensurePathNodeTracked(topNode: PathNode<Statement>): TreeNode<State, Statement> {
        var pathNode = topNode
        val pathNodesToAdd = mutableListOf<PathNode<Statement>>()
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
        val treeNode = ensurePathNodeTracked(state.pathNode)
        stateToLastNode[state] = treeNode
        treeNode.value.states += state
    }

    fun add(states: Collection<State>) {
        states.forEach(::addState)
    }

    fun rootNode() = rootNode.value.pathNode

    fun childrenOf(pathNode: PathNode<Statement>): List<PathNode<Statement>> =
        pathNodeToNode[pathNode]?.children?.values?.map { it.value.pathNode } ?: emptyList()

    fun statesAt(pathNode: PathNode<Statement>): Collection<State> =
        pathNodeToNode[pathNode]?.value?.states ?: emptyList()

    fun representative(pathNode: PathNode<Statement>): PathNode<Statement>? = pathNodeToNode[pathNode]?.value?.pathNode
}
