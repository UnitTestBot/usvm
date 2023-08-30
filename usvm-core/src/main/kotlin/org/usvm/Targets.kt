package org.usvm

import org.usvm.statistics.UMachineObserver

/**
 * Base class for a symbolic execution target. A target can be understood as a 'task' for symbolic machine
 * which it tries to complete. For example, a task can have an attached location which should be visited by a state
 * to consider the task completed. Also, the targets can produce some effects on states visiting them.
 *
 * Tasks can have 'child' tasks which should be completed only after its parent has been completed. For example,
 * it allows to force the execution along the specific path.
 *
 * Targets are designed to be shared between all the symbolic execution states. Due to this, once there is
 * a state which has reached the target which has no children, it is logically removed from the targets tree.
 * The other states ignore such removed targets.
 */
abstract class UTarget<Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, Method, Statement, *, Target, State>>(
    /**
     * Optional location of the target.
     */
    val location: Pair<Method, Statement>? = null
) : UMachineObserver<State> {
    private val childrenImpl = mutableListOf<Target>()
    private var parent: Target? = null

    /**
     * List of the child targets which should be reached after this target.
     */
    val children: List<Target> = childrenImpl

    /**
     * True if this target has no children.
     */
    val isSink get() = childrenImpl.isEmpty()

    /**
     * True if this target is logically removed from the tree.
     */
    var isRemoved = false
        private set

    /**
     * Adds a child target to this target.
     * TODO: avoid possible recursion
     *
     * @return this target (for convenient target tree building).
     */
    fun addChild(child: Target): Target {
        check(!isRemoved) { "Cannot add child to removed target" }
        require(child.parent == null) { "Cannot add child target with existing parent" }
        childrenImpl.add(child)
        @Suppress("UNCHECKED_CAST")
        child.parent = this as Target
        return child
    }

    protected inline fun forEachChild(action: Target.() -> Unit) {
        children.forEach { if (!it.isRemoved) it.action() }
    }

    /**
     * This method should be called by concrete targets to signal that [byState]
     * should try to visit the target. If the target without children has been
     * visited, it is logically removed from tree.
     *
     * TODO: think about naming
     */
    protected fun visit(byState: State) {
        @Suppress("UNCHECKED_CAST")
        if (byState.visitTarget(this as Target) && isSink) {
            remove()
        }
    }

    private fun remove() {
        check(childrenImpl.all { it.isRemoved }) { "Cannot remove target when some of its children are not removed" }
        if (isRemoved) {
            return
        }
        isRemoved = true
        val parent = parent
        if (parent != null && parent.childrenImpl.all { it.isRemoved }) {
            parent.remove()
        }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        forEachChild { onState(parent, forks) }
    }

    override fun onStateTerminated(state: State) {
        forEachChild { onStateTerminated(state) }
    }
}
