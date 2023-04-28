package org.usvm.ps

import org.usvm.UPathSelector

class DfsPathSelector<State> : UPathSelector<State> {
    private val stack: MutableList<State> = mutableListOf()

    override fun isEmpty() = stack.isEmpty()

    override fun peek() = stack.last()

    // State here is for verification only, we do not search using it
    // TODO probably, we have to lift up checkLast... function to the interface
    //      and implement this check there
    override fun terminate(state: State) {
        checkLastElementEquality(state)

        stack.removeLast()
    }

    override fun queue(): List<State> = stack

    override fun add(sourceState: State?, producedStates: Collection<State>) {
        if (sourceState == null && producedStates.isEmpty()) return

        if (stack.isEmpty()) {
            // during initialization of the stack
            // we should not have any additional states
            require(producedStates.isEmpty())

            stack += requireNotNull(sourceState)
            return
        }

        if (sourceState != null) {
            checkLastElementEquality(sourceState)
        }

        stack.addAll(producedStates)
    }

    override fun close() {
        // nothing to do
    }

    private fun checkLastElementEquality(state: State) {
        val lastElement = stack.lastOrNull()

        require(lastElement === state)
    }
}
