package org.usvm.statistics

import org.usvm.UState

class StateVisitsStatistics<Method, Statement, State : UState<*, Method, Statement, *, *, State>> :
    UMachineObserver<State> {
    private val visitedStatements = HashSet<Statement>()

    fun isVisited(statement: Statement) = visitedStatements.contains(statement)

    override fun onState(parent: State, forks: Sequence<State>) {
        visitedStatements.add(parent.currentStatement)
    }
}
