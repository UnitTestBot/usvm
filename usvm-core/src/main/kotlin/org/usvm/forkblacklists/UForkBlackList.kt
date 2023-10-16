package org.usvm.forkblacklists

import org.usvm.UState

/**
 * @see shouldForkTo
 */
interface UForkBlackList<State : UState<*, *, Statement, *, *, State>, Statement> {

    /**
     * Determines if the [state] should fork to the branch with location of [stmt].
     */
    fun shouldForkTo(state: State, stmt: Statement): Boolean

    companion object {
        fun <State : UState<*, *, Statement, *, *, State>, Statement> createDefault() = object :
            UForkBlackList<State, Statement> {
            override fun shouldForkTo(state: State, stmt: Statement): Boolean = true
        }
    }
}
