package org.usvm

import org.usvm.statistics.UMachineObserver

abstract class UTarget<Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, *, Method, Statement, *, Target, State>>(
    val method: Method,
    val statement: Statement
) : UMachineObserver<State> {
    private val childrenImpl = mutableListOf<Target>()
    private var parent: Target? = null

    val location = method to statement

    val children: List<Target> = childrenImpl

    val isSink get() = childrenImpl.isEmpty()

    var isRemoved = false
        private set

    // TODO: avoid possible recursion
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

    // TODO: think about naming
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
