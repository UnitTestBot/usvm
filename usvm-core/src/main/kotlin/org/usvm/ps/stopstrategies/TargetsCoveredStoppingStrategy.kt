package org.usvm.ps.stopstrategies

import org.usvm.ps.statistics.Statistics
import org.usvm.statistics.ApplicationGraph

class TargetsCoveredStoppingStrategy<Method, Statement>(
    methods: List<Method>,
    graph: ApplicationGraph<Method, Statement>,
) : Statistics<Method, Statement>(graph), StoppingStrategy {
    private val uncoveredStatements = methods
        .flatMap { graph.statementsOf(it) }
        .toMutableSet()

    val uncoveredStatementsCount get() = uncoveredStatements.size

<<<<<<< f42416ecdce887badc21e79d80d1fe85cd377b1e:usvm-core/src/main/kotlin/org/usvm/ps/stopstrategies/TargetsCoveredStoppingStrategy.kt
=======
    private fun findAllStatementsOfMethod(method: Method): Collection<Statement> {
        val entryStatements = graph.entryPoints(method)
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

>>>>>>> Add shortest distance to targets weighter:usvm-core/src/main/kotlin/org/usvm/ps/stopstregies/TargetsCoveredStoppingStrategy.kt
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