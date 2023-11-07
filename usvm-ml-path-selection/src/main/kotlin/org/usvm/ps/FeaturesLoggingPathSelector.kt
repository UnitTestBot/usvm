package org.usvm.ps

import org.usvm.*
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.util.LOG_BASE
import org.usvm.util.average
import org.usvm.util.getLast
import org.usvm.util.log

open class FeaturesLoggingPathSelector<State : UState<*, Method, Statement, *, *, State>, Statement, Method>(
    private val pathsTreeRoot: PathsTrieNode<State, Statement>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    cfgStatistics: CfgStatistics<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>,
    private val mlConfig: MLConfig,
    private val pathSelector: UPathSelector<State>
) : UPathSelector<State> {
    protected val lru = mutableListOf<State>()

    private val allStatements: List<Statement>
    private val visitedStatements = HashSet<Statement>()
    private var coveredStatementsCount = 0

    internal val path = mutableListOf<ActionData>()
    protected val probabilities = mutableListOf<List<Float>>()

    private val method: Method

    private var stepCount = 0

    private val penalty = 0.0f
    private var finishedStatesCount = 0u
    private var allStatesCount = 0u
    private val distanceCalculator = CallStackDistanceCalculator(
        targets = coverageStatistics.getUncoveredStatements(),
        cfgStatistics = cfgStatistics,
        applicationGraph
    )
    private val stateLastNewStatement = mutableMapOf<State, Int>()
    private val statePathCoverage = mutableMapOf<State, UInt>().withDefault { 0u }
    private val stateForkCount = mutableMapOf<State, UInt>().withDefault { 0u }
    private val statementFinishCounts = mutableMapOf<Statement, UInt>().withDefault { 0u }
    private val distancesToExit: Map<Statement, UInt>
    private val forkCountsToExit: Map<Statement, UInt>
    private val minForkCountsToExit: Map<Statement, UInt>
    private val statementRepetitions = mutableMapOf<Statement, UInt>().withDefault { 0u }
    private val subpathCounts = mutableMapOf<List<Statement>, UInt>().withDefault { 0u }

    protected val blockGraph: BlockGraph<Method, Statement>
    protected val graphFeaturesList = mutableListOf<List<BlockFeatures>>()

    private val featuresLogger: FeaturesLogger<State, Statement, Method>

    init {
        coverageStatistics.addOnCoveredObserver { _, method, statement ->
            distanceCalculator.removeTarget(method, statement)
        }
        allStatements = coverageStatistics.getUncoveredStatements().toList()
        method = applicationGraph.methodOf(allStatements.first())
        val (tmpDistancesToExit, tmpForkCountsToExit) = getDistancesToExit()
        distancesToExit = tmpDistancesToExit
        forkCountsToExit = tmpForkCountsToExit
        minForkCountsToExit = getMinForkCountsToExit()
        blockGraph = BlockGraph(
            applicationGraph, coverageStatistics,
            applicationGraph.entryPoints(method).first(), forkCountsToExit, minForkCountsToExit
        )
        graphFeaturesList.add(blockGraph.getGraphFeatures())
        featuresLogger = FeaturesLogger(method, blockGraph, mlConfig)
    }

    private fun getDistancesToExit(): Array<Map<Statement, UInt>> {
        val exits = applicationGraph.exitPoints(method)
        val statementsQueue = ArrayDeque<Statement>()
        val distancesToExit = mutableMapOf<Statement, UInt>().withDefault { 0u }
        val forkCountsToExit = mutableMapOf<Statement, UInt>().withDefault { 0u }
        statementsQueue.addAll(exits)
        while (statementsQueue.isNotEmpty()) {
            val currentStatement = statementsQueue.removeFirst()
            val distance = distancesToExit.getValue(currentStatement) + 1u
            val lastForkCount = forkCountsToExit.getValue(currentStatement)
            applicationGraph.predecessors(currentStatement).forEach { statement ->
                if (distancesToExit.contains(statement)) {
                    return@forEach
                }
                distancesToExit[statement] = distance
                val isFork = applicationGraph.successors(statement).count() > 1
                forkCountsToExit[statement] = lastForkCount + if (isFork) 1u else 0u
                statementsQueue.add(currentStatement)
            }
        }
        return arrayOf(distancesToExit, forkCountsToExit)
    }

    private fun getMinForkCountsToExit(): Map<Statement, UInt> {
        val exits = applicationGraph.exitPoints(method)
        val statementsQueue = ArrayDeque<Statement>()
        val forkCountsToExit = mutableMapOf<Statement, UInt>().withDefault { 0u }
        statementsQueue.addAll(exits)
        while (statementsQueue.isNotEmpty()) {
            val currentStatement = statementsQueue.removeFirst()
            val lastForkCount = forkCountsToExit.getValue(currentStatement)
            applicationGraph.predecessors(currentStatement).forEach { statement ->
                val isFork = applicationGraph.successors(statement).count() > 1
                val newForkCount = lastForkCount + if (isFork) 1u else 0u
                if (forkCountsToExit.contains(statement) || newForkCount > forkCountsToExit.getValue(statement)) {
                    return@forEach
                }
                forkCountsToExit[statement] = newForkCount
                if (isFork) {
                    statementsQueue.add(currentStatement)
                } else {
                    statementsQueue.addFirst(currentStatement)
                }
            }
        }
        return forkCountsToExit
    }

    // Reward feature calculation, not actual reward
    private fun getReward(state: State): Float {
        val statement = state.currentStatement
        if (statement === null ||
            (applicationGraph.successors(statement).toList().size +
                    applicationGraph.callees(statement).toList().size != 0) ||
            applicationGraph.methodOf(statement) != method ||
            state.callStack.size != 1
        ) {
            return 0.0f
        }
        return coverageStatistics.getUncoveredStatements().toSet()
            .intersect(state.reversedPath.asSequence().toSet()).size.toFloat()
    }

    private fun getStateFeatures(state: State): StateFeatures {
        val currentStatement = state.currentStatement!!
        val currentBlock = blockGraph.getBlock(currentStatement)
        val currentPath = state.reversedPath.asSequence().toList().reversed()

        val predecessorsCount = applicationGraph.predecessors(currentStatement).count()
        val successorsCount = applicationGraph.successors(currentStatement).count()
        val calleesCount = applicationGraph.callees(currentStatement).count()
        val logicalConstraintsLength = state.pathConstraints.logicalConstraints.size
        val stateTreeDepth = state.pathLocation.depth
        val statementRepetitionLocal = currentPath.filter { statement ->
            statement == currentStatement
        }.size
        val statementRepetitionGlobal = statementRepetitions.getValue(currentStatement)
        val distanceToUncovered = distanceCalculator.calculateDistance(state.currentStatement, state.callStack)
        val lastNewDistance = if (stateLastNewStatement.contains(state)) {
            currentPath.size - stateLastNewStatement.getValue(state)
        } else {
            1 / LOG_BASE - 1 // Equal to -1 after log
        }
        val pathCoverage = statePathCoverage.getValue(state)
        val distanceToBlockEnd = (currentBlock?.path?.size ?: 1) - 1 -
                (currentBlock?.path?.indexOf(currentStatement) ?: 0)
        val distanceToExit = distancesToExit.getValue(currentStatement)
        val forkCount = stateForkCount.getValue(state)
        val statementFinishCount = statementFinishCounts.getValue(currentStatement)
        val forkCountToExit = forkCountsToExit.getValue(currentStatement)
        val minForkCountToExit = minForkCountsToExit.getValue(currentStatement)
        val subpathCount2 = if (currentPath.size >= 2) subpathCounts.getValue(currentPath.getLast(2)) else 0u
        val subpathCount4 = if (currentPath.size >= 4) subpathCounts.getValue(currentPath.getLast(4)) else 0u
        val subpathCount8 = if (currentPath.size >= 8) subpathCounts.getValue(currentPath.getLast(8)) else 0u

        val reward = getReward(state)

        return StateFeatures(
            predecessorsCount.log(),
            successorsCount.log(),
            calleesCount.log(),
            logicalConstraintsLength.log(),
            stateTreeDepth.log(),
            statementRepetitionLocal.log(),
            statementRepetitionGlobal.log(),
            distanceToUncovered.log(),
            lastNewDistance.log(),
            pathCoverage.log(),
            distanceToBlockEnd.log(),
            distanceToExit.log(),
            forkCount.log(),
            statementFinishCount.log(),
            forkCountToExit.log(),
            minForkCountToExit.log(),
            subpathCount2.log(),
            subpathCount4.log(),
            subpathCount8.log(),
            reward.log(),
        )
    }

    private fun getStateFeatureQueue(): List<StateFeatures> {
        return lru.map { state ->
            getStateFeatures(state)
        }
    }

    private fun getGlobalStateFeatures(stateFeatureQueue: List<StateFeatures>): GlobalStateFeatures {
        val uncoveredStatements = coverageStatistics.getUncoveredStatements().toSet()

        val logFinishedStatesCount = finishedStatesCount.log()
        val finishedStatesFraction = finishedStatesCount.toFloat() / allStatesCount.toFloat()
        val totalCoverage = coverageStatistics.getTotalCoverage() / 100
        val visitedStatesFraction = visitedStatements.intersect(uncoveredStatements).size.toFloat() / allStatements.size

        return GlobalStateFeatures(
            stateFeatureQueue.map { it.logLogicalConstraintsLength }.average(),
            stateFeatureQueue.map { it.logStateTreeDepth }.average(),
            stateFeatureQueue.map { it.logStatementRepetitionLocal }.average(),
            stateFeatureQueue.map { it.logStatementRepetitionGlobal }.average(),
            stateFeatureQueue.map { it.logDistanceToUncovered }.average(),
            stateFeatureQueue.map { it.logLastNewDistance }.average(),
            stateFeatureQueue.map { it.logPathCoverage }.average(),
            stateFeatureQueue.map { it.logDistanceToBlockEnd }.average(),
            stateFeatureQueue.map { it.logReward }.average(),
            stateFeatureQueue.map { it.logSubpathCount2 }.average(),
            stateFeatureQueue.map { it.logSubpathCount4 }.average(),
            stateFeatureQueue.map { it.logSubpathCount8 }.average(),
            logFinishedStatesCount,
            finishedStatesFraction,
            visitedStatesFraction,
            totalCoverage,
        )
    }

    protected open fun getExtraFeatures(): List<Float> {
        return listOf()
    }

    private fun getActionData(
        stateFeatureQueue: List<StateFeatures>,
        globalStateFeatures: GlobalStateFeatures,
        chosenState: State
    ): ActionData {
        val stateId = lru.indexOfFirst { it.id == chosenState.id }
        return ActionData(
            stateFeatureQueue,
            globalStateFeatures,
            stateId,
            0.0f,
            graphFeaturesList.lastIndex,
            lru.map { it.currentStatement!! }.map { blockGraph.getBlock(it)?.id ?: -1 },
            getExtraFeatures()
        )
    }

    internal open fun getAllFeatures(stateFeatures: StateFeatures, actionData: ActionData, blockId: Int): List<Float> {
        return stateFeaturesToFloatList(stateFeatures) + globalStateFeaturesToFloatList(actionData.globalStateFeatures)
    }

    private fun updateCoverage(state: State) {
        val statePath = state.reversedPath.asSequence().toList().reversed()

        arrayOf(2, 4, 8).forEach { length ->
            if (statePath.size < length) {
                return@forEach
            }
            val subpath = statePath.getLast(length)
            subpathCounts[subpath] = subpathCounts.getValue(subpath) + 1u
        }

        val statement = state.currentStatement!!
        statementRepetitions[statement] = statementRepetitions.getValue(statement) + 1u
        visitedStatements.add(statement)

        if (applicationGraph.successors(statement).count() > 1) {
            stateForkCount[state] = stateForkCount.getValue(state) + 1u
        }

        if (coverageStatistics.getUncoveredStatements().contains(statement)) {
            stateLastNewStatement[state] = statePath.size
            statePathCoverage[state] = statePathCoverage.getValue(state) + 1u
        }
    }

    protected open fun getExtraNodeInfo(node: PathsTrieNode<State, Statement>) =
        node.states.joinToString(separator = "") { state -> ", ${state.id}" }

    private fun saveGraph() {
        featuresLogger.saveGraph(pathsTreeRoot, stepCount) { node ->
            getExtraNodeInfo(node)
        }
        stepCount += 1
    }

    fun savePath() {
        featuresLogger.savePath(path, blockGraph, probabilities, allStatements.size, graphFeaturesList)
        { stateFeatures, actionData, blockId ->
            getAllFeatures(stateFeatures, actionData, blockId)
        }
    }

    internal fun beforePeek(): Pair<List<StateFeatures>, GlobalStateFeatures> {
        if (mlConfig.graphUpdate == GraphUpdate.TestGeneration && (path.lastOrNull()?.reward ?: 0.0f) > 0.5f) {
            graphFeaturesList.add(blockGraph.getGraphFeatures())
        }
        val stateFeatureQueue = getStateFeatureQueue()
        return Pair(stateFeatureQueue, getGlobalStateFeatures(stateFeatureQueue))
    }

    internal fun afterPeek(
        state: State,
        stateFeatureQueue: List<StateFeatures>,
        globalStateFeatures: GlobalStateFeatures
    ) {
        val actionData = getActionData(stateFeatureQueue, globalStateFeatures, state)
        path.add(actionData)
        updateCoverage(state)
        if (stepCount < 100) {
            saveGraph()
        }
        lru.remove(state)
        lru.add(state)
    }

    override fun isEmpty(): Boolean {
        pathSelector.isEmpty()
        return lru.isEmpty()
    }

    override fun peek(): State {
        val (stateFeatureQueue, globalStateFeatures) = beforePeek()
        val state = pathSelector.peek()
        afterPeek(state, stateFeatureQueue, globalStateFeatures)
        return state
    }

    override fun update(state: State) {
        pathSelector.update(state)
    }

    override fun add(states: Collection<State>) {
        pathSelector.add(states)
        lru.addAll(states)
        allStatesCount += states.size.toUInt()
    }

    override fun remove(state: State) {
        pathSelector.remove(state)
        lru.remove(state)
        finishedStatesCount += 1u
        state.reversedPath.asSequence().toSet().forEach { statement ->
            statementFinishCounts[statement] = statementFinishCounts.getValue(statement) + 1u
        }

        // Actual reward calculation, change it in accordance to metrics
        val newCoveredStatementsCount = (allStatements.size - coverageStatistics.getUncoveredStatements().size)
        path.last().reward = (newCoveredStatementsCount - coveredStatementsCount).toFloat()
        coveredStatementsCount = newCoveredStatementsCount
    }
}
