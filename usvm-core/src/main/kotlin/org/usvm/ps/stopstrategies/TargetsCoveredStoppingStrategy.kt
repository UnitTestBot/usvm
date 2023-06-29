package org.usvm.ps.stopstrategies

import org.usvm.ApplicationGraph
import org.usvm.ps.statistics.Statistics

class TargetsCoveredStoppingStrategy<Method, Statement>(
    methods: List<Method>,
    graph: ApplicationGraph<Method, Statement>,
) : Statistics<Method, Statement>(graph), StoppingStrategy {
    private val uncoveredStatements = methods
        .flatMap { graph.statementsOf(it) }
        .toMutableSet()

    val uncoveredStatementsCount get() = uncoveredStatements.size

    override fun shouldStop(): Boolean = uncoveredStatements.isEmpty()

    override fun onStatementVisit(statement: Statement) {
        // do nothing, because statement is visited doesn't mean it will be covered
    }

    override fun onStatementCovered(statement: Statement) {
        uncoveredStatements -= statement
    }

    override fun onMethodVisit(method: Method) {
    }

    override fun recalculate() {
    }
}