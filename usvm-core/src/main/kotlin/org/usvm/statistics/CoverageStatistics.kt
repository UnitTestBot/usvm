package org.usvm.statistics

import org.usvm.UState
import org.usvm.algorithms.bfsTraversal
import java.util.concurrent.ConcurrentHashMap

/**
 * [UMachineObserver] which tracks coverage of specified methods. Statements are
 * considered covered when state visited them is terminated.
 *
 * Operations are thread-safe.
 *
 * @param methods methods to track coverage of.
 * @param applicationGraph [ApplicationGraph] used to retrieve statements by method.
 */
class CoverageStatistics<Method, Statement, State : UState<*, Method, Statement, *, *, State>>(
    methods: Set<Method>,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
) : UMachineObserver<State> {

    private val onStatementCoveredObservers: MutableSet<(State, Method, Statement) -> Unit> = ConcurrentHashMap.newKeySet()

    // Set is actually concurrent
    private val uncoveredStatements = HashMap<Method, MutableSet<Statement>>()
    private val coveredStatements = HashMap<Method, HashSet<Statement>>()

    private var totalUncoveredStatements = 0
    private var totalCoveredStatements = 0

    /**
     * Methods which coverage is currently tracked.
     */
    val coverageZone: List<Method> get() = coveredStatements.keys.toList()

    init {
        for (method in methods) {
            addCoverageZone(method)
        }
    }

    /**
     * Adds a new zone of coverage retrieved from the method.
     * This method might be useful to add coverage zones dynamically,
     * e.g., like in the [TransitiveCoverageZoneObserver].
     */
    fun addCoverageZone(method: Method) {
        if (method in uncoveredStatements) return

        val methodStatements = bfsTraversal(
            applicationGraph.entryPoints(method).toList(),
            applicationGraph::successors
        ).fold(ConcurrentHashMap.newKeySet<Statement>() as MutableSet<Statement>) { acc, stmt ->
            acc.add(stmt); acc
        }

        uncoveredStatements[method] = methodStatements
        totalUncoveredStatements += methodStatements.size
        coveredStatements[method] = hashSetOf()
    }

    private fun computeCoverage(covered: Int, uncovered: Int): Float {
        if (uncovered == 0) {
            return 100f
        }
        return covered.toFloat() / ((covered + uncovered).toFloat()) * 100f
    }

    /**
     * Returns current total coverage of all methods in coverage zone (in percents).
     */
    fun getTotalCoverage(): Float {
        return computeCoverage(totalCoveredStatements, totalUncoveredStatements)
    }

    /**
     * Returns current number of covered statements of all methods in coverage zone.
     */
    fun getTotalCoveredStatements(): Int = totalCoveredStatements

    /**
     * Returns current coverage of specified method (in percents).
     *
     * @param method one of the methods in coverage zone to get coverage of.
     */
    fun getMethodCoverage(method: Method): Float {
        val uncoveredStatementsCount = uncoveredStatements[method]?.size ?: throw IllegalArgumentException("Trying to get coverage of unknown method $method")
        return computeCoverage(coveredStatements.getValue(method).size, uncoveredStatementsCount)
    }

    /**
     * Returns current coverage (in percents) of the [method] and all the methods in coverage zone
     * transitively reachable from it in call graph.
     *
     * @param method one of the methods in coverage zone to get transitive coverage of.
     */
    fun getTransitiveMethodCoverage(method: Method): Float {
        var uncoveredStatementsCountAcc = 0
        var coveredStatementsCountAcc = 0
        bfsTraversal(listOf(method)) {
            applicationGraph.statementsOf(method).flatMap(applicationGraph::callees).filter(uncoveredStatements::containsKey)
        }.forEach {
            uncoveredStatementsCountAcc += uncoveredStatements.getValue(it).size
            coveredStatementsCountAcc += coveredStatements.getValue(it).size
        }
        return computeCoverage(coveredStatementsCountAcc, uncoveredStatementsCountAcc)
    }

    /**
     * Returns statements from methods in coverage zone which have not been covered yet.
     */
    fun getUncoveredStatements(): Collection<Statement> {
        return uncoveredStatements.values.flatten()
    }

    /**
     * Adds a listener triggered when a new statement is covered.
     */
    fun addOnCoveredObserver(observer: (State, Method, Statement) -> Unit) {
        onStatementCoveredObservers.add(observer)
    }

    // TODO: don't consider coverage of runtime exceptions states
    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (!stateReachable) return

        val statements = state.pathNode.allStatements
        for (statement in statements) {
            val method = applicationGraph.methodOf(statement)

            if (uncoveredStatements[method]?.remove(statement) != true) {
                continue
            }

            totalUncoveredStatements--
            totalCoveredStatements++
            coveredStatements.getValue(method).add(statement)
            onStatementCoveredObservers.forEach { it(state, method, statement) }
        }
    }
}
