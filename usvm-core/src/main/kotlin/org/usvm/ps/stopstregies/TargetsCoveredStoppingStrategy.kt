package org.usvm.ps.stopstregies

import org.usvm.ApplicationGraph
import org.usvm.ps.statistics.Statistics

class TargetsCoveredStoppingStrategy<Method, Statement>(
    methods: List<Method>,
    graph: ApplicationGraph<Method, Statement>,
) : Statistics<Method, Statement>(graph), StoppingStrategy {
    private val uncoveredStatements = methods
        .flatMap { findAllStatementsOfMethod(it) }
        .toMutableSet()

    val uncoveredStatementsCount get() = uncoveredStatements.size

    private fun findAllStatementsOfMethod(method: Method): Collection<Statement> {
        val entryStatements = graph.entryPoint(method)
        val statements = entryStatements.toMutableSet()

        val queue = ArrayDeque(entryStatements.toList())

        while (queue.isNotEmpty()) {
            val statement = queue.removeLast()
            val successors = graph.successors(statement)
            for (successor in successors) {
                if (successor !in statements) {
                    statements += successor
                    queue += successor
                }
            }
        }

        return statements
    }

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