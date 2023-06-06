package org.usvm.statistics

import org.usvm.UState

interface StatisticsObserver<Method, Statement> {
    fun onStatementCovered(method: Method, statement: Statement) { }
    fun onStateTerminated(state: UState<*, *, Method, Statement>) { }
    fun onStep(state: UState<*, *, Method, Statement>) { }
}