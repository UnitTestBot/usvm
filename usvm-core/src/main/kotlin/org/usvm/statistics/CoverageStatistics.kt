package org.usvm.statistics

import org.usvm.UState
import org.usvm.util.bfsTraversal
import java.util.concurrent.ConcurrentHashMap

class CoverageStatistics<Method, Statement, State : UState<*, *, Method, Statement>>(
    methods: Set<Method>,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val statisticsObservable: StatisticsObservable<Method, Statement, State>,
) : StatisticsObserver<Method, Statement, State> {

    // Set is actually concurrent
    private val uncoveredStatements = HashMap<Method, MutableSet<Statement>>()

    private val coveredStatements = HashMap<Method, HashSet<Statement>>()

    private var totalUncoveredStatements = 0
    private var totalCoveredStatements = 0

    init {
        for (method in methods) {
            val methodStatements = bfsTraversal(applicationGraph.entryPoints(method).toList(), applicationGraph::successors)
                .fold(ConcurrentHashMap.newKeySet<Statement>() as MutableSet<Statement>) { acc, stmt -> acc.add(stmt); acc  }
            uncoveredStatements[method] = methodStatements
            totalUncoveredStatements += methodStatements.size
            coveredStatements[method] = HashSet()
        }
    }

    private fun computeCoverage(covered: Int, uncovered: Int): Float {
        if (uncovered == 0) {
            return 100f
        }
        return covered.toFloat() / uncovered.toFloat() * 100f
    }

    fun getTotalCoverage(): Float {
        return computeCoverage(totalCoveredStatements, totalUncoveredStatements)
    }

    fun getMethodCoverage(method: Method): Float {
        val uncoveredStatementsCount = uncoveredStatements[method]?.size ?: throw IllegalArgumentException("Trying to get coverage of unknown method")
        return computeCoverage(coveredStatements.getValue(method).size, uncoveredStatementsCount)
    }

    fun getUncoveredStatements(): Collection<Pair<Method, Statement>> {
        return uncoveredStatements.flatMap { kvp -> kvp.value.map { kvp.key to it } }
    }

    override fun onStateTerminated(state: State) {
        for (statement in state.path) {
            val method = applicationGraph.methodOf(statement)

            val isRemoved = uncoveredStatements[method]?.remove(statement) ?: throw IllegalArgumentException("Trying to pass unknown method to coverage statistics")
            if (!isRemoved) {
                continue
            }

            totalUncoveredStatements--
            totalCoveredStatements++
            coveredStatements.getValue(method).add(statement)
            statisticsObservable.onStatementCovered(method, statement)
        }
    }
}