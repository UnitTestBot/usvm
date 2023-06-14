package org.usvm.statistics

import org.usvm.UState

interface StatisticsObserver<Method, Statement, State : UState<*, *, Method, Statement>> {
    fun onStatementCovered(method: Method, statement: Statement) { }
    fun onStateTerminated(state: State) { }
    fun onStateForked(parent: State, forks: Collection<State>) { }
    fun onStep(state: State) { }
}