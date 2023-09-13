package org.usvm

/**
 * Symbolic execution tree node.
 */
sealed class PathsTrieNode<State : UState<*, *, Statement, *, *, State>, Statement> {
    /**
     * Forked states' nodes.
     */
    abstract val children: MutableMap<Statement, PathsTrieNodeImpl<State, Statement>>

    /**
     * States which are located in this node.
     */
    abstract val states: MutableSet<State>

    /**
     * Statement of the current node in the execution tree.
     */
    abstract val statement: Statement

    /**
     * The node which has this node as a child.
     */
    abstract val parent: PathsTrieNode<State, Statement>?

    /**
     * Labels which can be used to exclude some tree branches from traversal.
     */
    abstract val labels: Set<Any>

    /**
     * Node's depth in the tree. 0 for root node, 1 for its children etc.
     */
    abstract val depth: Int

    /**
     * Adds a new label to [labels] collection.
     */
    abstract fun addLabel(label: Any)

    /**
     * Propagates the [state] from the current node to a child using [statement].
     * If such a child existed before this operation, the [state] is added to it and the child will be returned.
     * Otherwise, we will create a new instance of [PathsTrieNodeImpl] with the single [state].
     *
     * Note that [state] will be removed from the [states] in the current node. All correct links between
     * the child node and the current one are added automatically.
     */
    fun pathLocationFor(statement: Statement, state: State): PathsTrieNodeImpl<State, Statement> {
        val child = children[statement]

        if (child != null) {
            child.states += state
            states -= state
            return child
        }

        val node = when (this) {
            is RootNode -> PathsTrieNodeImpl(parentNode = this, statement, state)
            is PathsTrieNodeImpl -> PathsTrieNodeImpl(parentNode = this, statement, state)
        }

        return node
    }
}

class PathsTrieNodeImpl<State : UState<*, *, Statement, *, *, State>, Statement> private constructor(
    override val depth: Int,
    override val states: MutableSet<State>,
    // Note: order is important for tests
    override val children: MutableMap<Statement, PathsTrieNodeImpl<State, Statement>> = mutableMapOf(),
    override val parent: PathsTrieNode<State, Statement>?,
    override val statement: Statement,
) : PathsTrieNode<State, Statement>() {
    internal constructor(parentNode: RootNode<State, Statement>, statement: Statement, state: State) : this(
        depth = parentNode.depth + 1,
        parent = parentNode,
        states = hashSetOf(state),
        statement = statement
    ) {
        parentNode.children[statement] = this
    }

    internal constructor(parentNode: PathsTrieNodeImpl<State, Statement>, statement: Statement, state: State) : this(
        depth = parentNode.depth + 1,
        parent = parentNode,
        states = mutableSetOf(state),
        statement = statement
    ) {
        parentNode.children[statement] = this
        parentNode.states -= state
    }

    override val labels: MutableSet<Any> = hashSetOf()

    override fun addLabel(label: Any) {
        labels.add(label)
    }

    override fun toString(): String = "Depth: $depth, statement: $statement"
}

class RootNode<State : UState<*, *, Statement, *, *, State>, Statement> : PathsTrieNode<State, Statement>() {
    override val children: MutableMap<Statement, PathsTrieNodeImpl<State, Statement>> = mutableMapOf()

    override val states: MutableSet<State> = hashSetOf()

    override val statement: Statement
        get() {
            throw UnsupportedOperationException("The root node must not contain a statement")
        }

    override val parent: PathsTrieNode<State, Statement>? = null

    override val labels: MutableSet<Any> = hashSetOf()

    override val depth: Int = 0

    override fun addLabel(label: Any) {
        labels += label
    }
}
