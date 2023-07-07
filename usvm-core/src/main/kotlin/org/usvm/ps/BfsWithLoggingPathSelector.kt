package org.usvm.ps

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.PathsTreeStatistics
import java.io.File

internal class BfsWithLoggingPathSelector<State : UState<*, *, *, Statement>, Statement>(
    private val pathsTreeStatistics: PathsTreeStatistics<*, Statement, State>,
    private val coverageStatistics: CoverageStatistics<*, Statement, State>,
    private val applicationGraph: ApplicationGraph<*, Statement>
) : UPathSelector<State> {
    private val queue = ArrayDeque<State>()

    private var allStmts: Collection<Any?>? = null
    private val coveredStmts = HashSet<Any?>()
    private var coverage = 0.0

    private val path = mutableListOf<ActionData>()

    private val filepath = "./paths_log/"
    private var filename: String? = null
    private val jsonFormat = Json { prettyPrint = true; isLenient = true }

    init {
        File(filepath).mkdirs()
    }

    @Serializable
    private data class StateFeatures(
        val successorsCount: UInt,
        val reward: Double
    )

    @Serializable
    private data class ActionData(
        val queue: List<StateFeatures>,
        val chosenStateId: Int,
    )

    private fun getStateFeatures(state: State): StateFeatures {
        val currentStatement = state.currentStatement
        val successorsCount: UInt = if (currentStatement === null) 0u else
            applicationGraph.successors(currentStatement).count().toUInt()
        val reward = if (coveredStmts.contains(state.currentStatement)) 0.0 else 1.0
        return StateFeatures(successorsCount, reward)
    }

    private fun getActionData(chosenState: State): ActionData {
        val stateFeaturePairs = queue.map { state ->
            Pair(state.id, getStateFeatures(state))
        }
        val stateId = stateFeaturePairs.indexOfFirst { it.first == chosenState.id }
        val stateFeatureQueue = stateFeaturePairs.map { it.second }
        return ActionData(stateFeatureQueue, stateId)
    }

    override fun isEmpty() = queue.isEmpty()

    override fun peek(): State {
        val state = queue.first()
        path.add(getActionData(state))
        savePath()
        updateCoverage(state)
        return state
    }

    private fun savePath() {
        if (path.isEmpty()) {
            return
        }
        val filename = applicationGraph.methodOf(queue.first().path.first()).hashCode()
        File("$filepath$filename.json")
            .writeText(jsonFormat.encodeToString(path))
    }

    private fun updateCoverage(state: State) {
        val uncoveredStmts = coverageStatistics.getUncoveredStatements()
        if (allStmts === null) {
            allStmts = uncoveredStmts.map { it.second }
        }
        val stmt = state.currentStatement
        coveredStmts.add(stmt)
        coverage = coveredStmts.size * 100.0 / (allStmts?.size ?: 1)
    }

    override fun update(state: State) { }

    override fun add(states: Collection<State>) {
        if (states.isEmpty()) {
            return
        }
        if (filename === null) {
            filename = applicationGraph.methodOf(states.first().path.first()).hashCode().toString()
        }
        queue.addAll(states)
    }

    override fun remove(state: State) {
        when (state) {
            queue.last() -> queue.removeLast() // fast remove from the tail
            queue.first() -> queue.removeFirst() // fast remove from the head
            else -> queue.remove(state)
        }
    }
}
