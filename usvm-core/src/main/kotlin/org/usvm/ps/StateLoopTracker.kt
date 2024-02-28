package org.usvm.ps

import org.usvm.UState

interface StateLoopTracker<Loop : Any, Statement, State : UState<*, *, Statement, *, *, State>> {
    /**
     * If [statement] is an entrypoint of some loop returns this loop.
     * Returns null if the statement doesn't belong to any loop or
     * the statement is in the loop body, but is not the first (header) statement
     * */
    fun findLoopEntrance(statement: Statement): Loop?

    /**
     * Returns true if the given [forkPoint] can affect the [loop] iteration number
     * (e.g. jump to the next iteration).
     * */
    fun isLoopIterationFork(loop: Loop, forkPoint: Statement): Boolean
}
