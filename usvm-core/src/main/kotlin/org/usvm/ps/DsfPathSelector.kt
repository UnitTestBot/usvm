package org.usvm.ps

import org.usvm.UState

class DsfPathSelector<State : UState<*, *, Method, Statement>, Method, Statement> :
    PathSelector<State, Method, Statement> {
    private val stack: MutableList<State> = mutableListOf()

    override fun peek(): State = stack.last()

    override fun isEmpty(): Boolean = stack.isEmpty()

    override fun queue(): Iterable<State> = stack

    override fun terminate(state: State) {
        checkLastElementEquality(state)

        stack.removeLast()
    }

    override fun update(sourceState: State, producedStates: Iterable<State>) {
        checkLastElementEquality(sourceState)

        stack.addAll(producedStates)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    private fun checkLastElementEquality(state: State) {
        val lastElement = stack.lastOrNull()

        require(lastElement === state)
    }
}
