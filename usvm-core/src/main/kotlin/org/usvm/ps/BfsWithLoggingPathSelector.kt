package org.usvm.ps

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics
import java.io.File
import kotlin.collections.ArrayDeque
import kotlin.collections.HashSet
import kotlin.math.log2

internal open class BfsWithLoggingPathSelector<State : UState<*, *, Method, Statement>, Statement, Method>(
    private val pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    private val distanceStatistics: DistanceStatistics<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) : UPathSelector<State> {
    protected val queue = ArrayDeque<State>()

    private var allStmts: Collection<Any?>? = null
    private val coveredStmts = HashSet<Any?>()
    private var coverage = 0.0

    protected val path = mutableListOf<ActionData>()

    private val filepath = "./paths_log/"
    private var filename: String? = null
    private val jsonScheme: JsonArray
    private var jsonFormat = Json {
        encodeDefaults = true
    }

    private val penalty = 0.0f
    private var finishedStatesCount = 0u
    private val allStates = mutableSetOf<State>()
    private val weighter = ShortestDistanceToTargetsStateWeighter(
        coverageStatistics.getUncoveredStatements(),
        distanceStatistics::getShortestCfgDistance,
        distanceStatistics::getShortestCfgDistanceToExitPoint
    )
    private val stateLastNewStatement = mutableMapOf<State, Int>()
    private val statePathCoverage = mutableMapOf<State, UInt>()

    @Serializable
    protected data class StateFeatures(
        val successorsCount: UInt = 0u,
        val finishedStatesCount: UInt = 0u,
        val logicalConstraintsLength: UInt = 0u,
        val stateTreeDepth: UInt = 0u,
        val statementRepetitionLocal: UInt = 0u,
        val statementRepetitionGlobal: UInt = 0u,
        val distanceToUncovered: Float = 0.0f,
        val lastNewDistance: Int = 0,
        val pathCoverage: UInt = 0u,
        val reward: Float = 0.0f
    )

    @Serializable
    protected data class AverageStateFeatures(
        val averageSuccessorsCount: Float = 0.0f,
        val averageLogicalConstraintsLength: Float = 0.0f,
        val averageStateTreeDepth: Float = 0.0f,
        val averageStatementRepetitionLocal: Float = 0.0f,
        val averageStatementRepetitionGlobal: Float = 0.0f,
        val averageDistanceToUncovered: Float = 0.0f,
        val averageLastNewDistance: Float = 0.0f,
        val averagePathCoverage: Float = 0.0f,
        val averageReward: Float = 0.0f
    )

    @Serializable
    protected data class ActionData(
        val queue: List<StateFeatures>,
        val averageStateFeatures: AverageStateFeatures,
        val chosenStateId: Int,
        val reward: Float
    )

    init {
        File(filepath).mkdirs()
        coverageStatistics.addOnCoveredObserver { _, method, statement ->
            weighter.removeTarget(method, statement)
        }
        jsonScheme = buildJsonArray {
            addJsonArray {
                jsonFormat.encodeToJsonElement(StateFeatures()).jsonObject.forEach { t, _ ->
                    add(t)
                }
                jsonFormat.encodeToJsonElement(AverageStateFeatures()).jsonObject.forEach { t, _ ->
                    add(t)
                }
            }
            add("chosenStateId")
            add("reward")
        }
    }

    protected open fun getReward(state: State): Float {
        return if (!coveredStmts.contains(state.currentStatement)) 1.0f else -penalty
    }

    private fun getStateFeatures(state: State): StateFeatures {
        val currentStatement = state.currentStatement

        val successorsCount = if (currentStatement === null) 0u else
            applicationGraph.successors(currentStatement).count().toUInt()
        val logicalConstraintsLength = state.pathConstraints.logicalConstraints.size.toUInt()
        val stateTreeDepth = pathsTreeStatistics.getStateDepth(state).toUInt()
        val statementRepetitionLocal = state.path.filter { statement ->
            statement == currentStatement
        }.size.toUInt()
        val statementRepetitionGlobal = allStates.sumOf { currentState ->
            currentState.path.filter { statement ->
                statement == currentStatement
            }.size.toUInt()
        }
        val distanceToUncovered = log2(weighter.weight(state).toFloat() + 1)
        val lastNewDistance = state.path.size - 1 - stateLastNewStatement.getOrDefault(state, -1)
        val pathCoverage = statePathCoverage.getOrDefault(state, 0u)

        if (!coveredStmts.contains(state.currentStatement)) {
            stateLastNewStatement[state] = state.path.size - 1
            statePathCoverage[state] = statePathCoverage.getOrDefault(state, 0u) + 1u
        }

        val reward = getReward(state)

        return StateFeatures (
            successorsCount,
            finishedStatesCount,
            logicalConstraintsLength,
            stateTreeDepth,
            statementRepetitionLocal,
            statementRepetitionGlobal,
            distanceToUncovered,
            lastNewDistance,
            pathCoverage,
            reward
        )
    }

    protected fun getStateFeatureQueue(): List<StateFeatures> {
        return queue.map { state ->
            getStateFeatures(state)
        }
    }

    protected fun getAverageStateFeatures(stateFeatureQueue: List<StateFeatures>): AverageStateFeatures {
        val queueSize = stateFeatureQueue.size
        return AverageStateFeatures(
            stateFeatureQueue.sumOf { it.successorsCount }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.logicalConstraintsLength }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.stateTreeDepth }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.statementRepetitionLocal }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.statementRepetitionGlobal }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.distanceToUncovered.toDouble() }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.lastNewDistance }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.pathCoverage }.toFloat() / queueSize,
            stateFeatureQueue.sumOf { it.reward.toDouble() }.toFloat() / queueSize,
        )
    }

    protected fun getActionData(stateFeatureQueue: List<StateFeatures>,
                                averageStateFeatures: AverageStateFeatures,
                                chosenState: State): ActionData {
        val stateId = queue.indexOfFirst { it.id == chosenState.id }
        return ActionData (
            stateFeatureQueue,
            averageStateFeatures,
            stateId,
            stateFeatureQueue[stateId].reward)
    }

    protected fun savePath() {
        if (path.isEmpty()) {
            return
        }
        val filename = applicationGraph.methodOf(queue.first().path.first()).hashCode()
        val jsonData = buildJsonObject {
            put("scheme", jsonScheme)
            putJsonArray("path") {
                path.forEach { actionData ->
                    addJsonArray {
                        addJsonArray {
                            actionData.queue.forEach { stateFeatures ->
                                addJsonArray {
                                    jsonFormat.encodeToJsonElement(stateFeatures).jsonObject.forEach { _, u ->
                                        add(u)
                                    }
                                    jsonFormat.encodeToJsonElement(actionData.averageStateFeatures).jsonObject.forEach { _, u ->
                                        add(u)
                                    }
                                }
                            }
                        }
                        add(actionData.chosenStateId)
                        add(actionData.reward)
                    }
                }
            }
        }
        File("$filepath$filename.json")
            .writeText(jsonFormat.encodeToString(jsonData))
    }

    protected fun updateCoverage(state: State) {
        if (allStmts === null) {
            val uncoveredStmts = coverageStatistics.getUncoveredStatements()
            allStmts = uncoveredStmts.map { it.second }
        }
        coveredStmts.add(state.currentStatement)
        coverage = coveredStmts.size * 100.0 / (allStmts?.size ?: 1)
    }

    override fun isEmpty() = queue.isEmpty()

    override fun peek(): State {
        val state = queue.first()
        val stateFeatureQueue = getStateFeatureQueue()
        val averageStateFeatures = getAverageStateFeatures(stateFeatureQueue)
        path.add(getActionData(stateFeatureQueue, averageStateFeatures, state))
        savePath()
        updateCoverage(state)
        return state
    }

    override fun update(state: State) {}

    override fun add(states: Collection<State>) {
        if (states.isEmpty()) {
            return
        }
        if (filename === null) {
            filename = applicationGraph.methodOf(states.first().path.first()).hashCode().toString()
        }
        queue.addAll(states)
        allStates.addAll(states)
    }

    override fun remove(state: State) {
        when (state) {
            queue.last() -> queue.removeLast() // fast remove from the tail
            queue.first() -> queue.removeFirst() // fast remove from the head
            else -> queue.remove(state)
        }
        finishedStatesCount += 1u
    }
}
