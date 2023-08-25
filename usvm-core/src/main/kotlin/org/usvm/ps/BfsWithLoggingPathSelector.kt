package org.usvm.ps

import io.github.rchowell.dotlin.digraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.usvm.*
import org.usvm.statistics.*
import java.io.File
import java.lang.UnsupportedOperationException
import java.nio.file.Path
import kotlin.collections.ArrayDeque
import kotlin.collections.HashSet
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.sqrt

private const val LOG_BASE = 1.42

private fun Collection<Float>.average(): Float {
    return this.sumOf { it.toDouble() }.toFloat() / this.size
}

private fun Collection<Float>.std(): Float {
    if (this.size <= 1) {
        return 0.0f
    }
    val average = this.average().toDouble()
    return sqrt(this.map { it.toDouble() }.fold(0.0) { a, b ->
        a + (b - average).pow(2)
    } / (this.size - 1)).toFloat()
}

private fun Number.log(): Float {
    return log(this.toDouble() + 1, LOG_BASE).toFloat()
}

private fun UInt.log(): Float {
    return this.toDouble().log()
}

private fun <T> List<T>.getLast(count: Int): List<T> {
    return this.subList(this.size - count, this.size)
}

@Suppress("LeakingThis")
internal open class BfsWithLoggingPathSelector<State : UState<*, *, Method, Statement, *, State>, Statement, Method>(
    private val pathsTreeRoot: PathsTrieNode<State, Statement>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    private val distanceStatistics: DistanceStatistics<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) : UPathSelector<State> {
    protected val queue = ArrayDeque<State>()
    protected val lru = mutableListOf<State>()

    private val allStatements: List<Statement>
    private val visitedStatements = HashSet<Statement>()

    protected val path = mutableListOf<ActionData>()
    protected val probabilities = mutableListOf<List<Float>>()

    private val filepath = Path(MainConfig.dataPath, "jsons").toString()
    protected val method: Method
    private val filename: String
    private val jsonStateScheme: JsonArray
    private val jsonTrajectoryScheme: JsonArray
    private var jsonFormat = Json {
        encodeDefaults = true
    }

    private var stepCount = 0
    private val graphsPath = Path(MainConfig.gameEnvPath, "graphs").toString()
    private val blockGraphsPath = Path(MainConfig.gameEnvPath, "block_graphs").toString()

    private val penalty = 0.0f
    private var finishedStatesCount = 0u
    private var allStatesCount = 0u
    private val weighter = ShortestDistanceToTargetsStateWeighter<Method, Statement, State>(
        coverageStatistics.getUncoveredStatements(),
        distanceStatistics::getShortestCfgDistance,
        distanceStatistics::getShortestCfgDistanceToExitPoint
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

    protected class BlockGraph<Method, Statement>(
        private val applicationGraph: ApplicationGraph<Method, Statement>,
        initialStatement: Statement,
        private val pathSelector: BfsWithLoggingPathSelector<*, Statement, Method>
    ) {
        val root: Block<Statement>
        private val successorsMap = mutableMapOf<Block<Statement>, List<Statement>>().withDefault { listOf() }
        private val predecessorsMap = mutableMapOf<Block<Statement>, MutableList<Statement>>()
            .withDefault { mutableListOf() }
        private val coveredStatements = mutableMapOf<Statement, Block<Statement>>()
        var currentBlockId = 0
        val blockList = mutableListOf<Block<Statement>>()

        init {
            root = buildBlocks(initialStatement)
        }

        private fun chooseNextStatement(statementQueue: ArrayDeque<Statement>): Statement? {
            var currentStatement = statementQueue.removeFirstOrNull()
            while (currentStatement != null && coveredStatements.contains(currentStatement)) {
                currentStatement = statementQueue.removeFirstOrNull()
            }
            return currentStatement
        }

        private fun addSuccessor(block: Block<Statement>, statement: Statement) {
            successorsMap[block] = successorsMap.getValue(block) + statement
        }

        private fun getPredecessors() {
            blockList.forEach { previousBlock ->
                val lastStatement = previousBlock.path.last()
                successors(previousBlock).forEach { nextBlock ->
                    predecessorsMap.getValue(nextBlock).add(lastStatement)
                }
            }
        }

        private fun buildBlocks(statement: Statement): Block<Statement> {
            var currentStatement = statement
            val statementQueue = ArrayDeque<Statement>()
            val rootBlock = Block(this)
            var currentBlock = rootBlock
            while (true) {
                if (coveredStatements.contains(currentStatement)) {
                    addSuccessor(currentBlock, currentStatement)
                    val nextStatement = chooseNextStatement(statementQueue) ?: break
                    currentStatement = nextStatement
                    currentBlock = Block(this)
                    continue
                }
                val predecessors = applicationGraph.predecessors(currentStatement).toList()
                val successors = applicationGraph.successors(currentStatement).toList()
                var newBlock = false
                predecessors.forEach { previousStatement ->
                    val previousBlock = coveredStatements[previousStatement]
                    if (previousBlock == currentBlock) {
                        return@forEach
                    }
                    newBlock = true
                }
                if (newBlock && currentBlock.path.isNotEmpty()) {
                    addSuccessor(currentBlock, currentStatement)
                    currentBlock = Block(this)
                }
                coveredStatements[currentStatement] = currentBlock
                currentBlock.path.add(currentStatement)
                if (successors.size > 1) {
                    statementQueue.addAll(successors)
                    successors.forEach {
                        addSuccessor(currentBlock, it)
                    }
                    val nextStatement = chooseNextStatement(statementQueue) ?: break
                    currentStatement = nextStatement
                    currentBlock = Block(this)
                } else if (successors.isEmpty()) {
                    val nextStatement = chooseNextStatement(statementQueue) ?: break
                    currentStatement = nextStatement
                    currentBlock = Block(this)
                } else {
                    currentStatement = successors.first()
                }
            }
            getPredecessors()
            return rootBlock
        }

        private fun predecessors(block: Block<Statement>): List<Block<Statement>> {
            return predecessorsMap.getValue(block).map { coveredStatements[it]!! }
        }

        fun successors(block: Block<Statement>): List<Block<Statement>> {
            return successorsMap.getValue(block).map { coveredStatements[it]!! }
        }

        fun getEdges(): Pair<List<Int>, List<Int>> {
            return blockList.flatMap { block ->
                predecessors(block).map { Pair(block.id, it.id) }
            }.unzip()
        }

        fun getBlock(statement: Statement): Block<Statement>? {
            return coveredStatements[statement]
        }

        private fun getBlockFeatures(block: Block<Statement>): BlockFeatures {
            val firstStatement = block.path.first()

            val length = block.path.size
            val predecessorsCount = predecessors(block).size
            val successorsCount = successors(block).size
            val totalCalleesCount = block.path.sumOf { pathSelector.applicationGraph.callees(it).count() }
            val forkCountToExit = pathSelector.forkCountsToExit.getValue(firstStatement)
            val minForkCountToExit = pathSelector.minForkCountsToExit.getValue(firstStatement)
            val isCovered = firstStatement !in pathSelector.coverageStatistics
                .getUncoveredStatements().map { it.second }

            return BlockFeatures(
                length.log(),
                predecessorsCount.log(),
                successorsCount.log(),
                totalCalleesCount.log(),
                forkCountToExit.log(),
                minForkCountToExit.log(),
                if (isCovered) 1.0f else 0.0f,
            )
        }

        fun getGraphFeatures(): List<BlockFeatures> {
            return blockList.map { getBlockFeatures(it) }
        }

        fun saveGraph(filePath: Path) {
            val nodes = mutableListOf<Block<Statement>>()
            val treeQueue = ArrayDeque<Block<Statement>>()
            treeQueue.add(root)
            val visitedBlocks = mutableSetOf<Block<Statement>>()
            visitedBlocks.add(root)
            while (treeQueue.isNotEmpty()) {
                val currentNode = treeQueue.removeFirst()
                nodes.add(currentNode)
                val successors = successors(currentNode)
                treeQueue.addAll(successors.filter { !visitedBlocks.contains(it) })
                visitedBlocks.addAll(successors)
            }
            val graph = digraph ("BlockGraph") {
                nodes.forEach { node ->
                    val nodeName = node.toString()
                    +nodeName
                    successors(node).forEach { child ->
                        val childName = child.toString()
                        nodeName - childName
                    }
                }
            }
            filePath.parent.toFile().mkdirs()
            filePath.writeText(graph.dot())
        }
    }

    @Serializable
    protected data class BlockFeatures(
        val logLength: Float = 0.0f,
        val logPredecessorsCount: Float = 0.0f,
        val logSuccessorsCount: Float = 0.0f,
        val logTotalCalleesCount: Float = 0.0f,
        val logForkCountToExit: Float = 0.0f,
        val logMinForkCountToExit: Float = 0.0f,
        val isCovered: Float = 0.0f,
    )

    protected data class Block<Statement>(
        val id: Int = 0,
        var path: MutableList<Statement> = mutableListOf()
    ) {
        constructor(blockGraph: BlockGraph<*, Statement>) : this(
            id = blockGraph.currentBlockId
        ) {
            blockGraph.currentBlockId += 1
            blockGraph.blockList.add(this)
        }

        private fun escape(input: String): String {
            val result = StringBuilder(input.length)
            input.forEach { ch ->
                result.append(when (ch) {
                    '\n' -> "\\n"
                    '\t' -> "\\t"
                    '\b' -> "\\b"
                    '\r' -> "\\r"
                    '\"' -> "\\\""
                    '\'' -> "\\\'"
                    '\\' -> "\\\\"
                    '$' -> "\\$"
                    else -> ch
                })
            }
            return result.toString()
        }

        override fun toString(): String {
            return "\"${id}: ${path.map { escape(it.toString()) }}\""
        }
    }

    @Serializable
    protected data class StateFeatures(
        val logPredecessorsCount: Float = 0.0f,
        val logSuccessorsCount: Float = 0.0f,
        val logCalleesCount: Float = 0.0f,
        val logLogicalConstraintsLength: Float = 0.0f,
        val logStateTreeDepth: Float = 0.0f,
        val logStatementRepetitionLocal: Float = 0.0f,
        val logStatementRepetitionGlobal: Float = 0.0f,
        val logDistanceToUncovered: Float = 0.0f,
        val logLastNewDistance: Float = 0.0f,
        val logPathCoverage: Float = 0.0f,
        val logDistanceToBlockEnd: Float = 0.0f,
        val logDistanceToExit: Float = 0.0f,
        val logForkCount: Float = 0.0f,
        val logStatementFinishCount: Float = 0.0f,
        val logForkCountToExit: Float = 0.0f,
        val logMinForkCountToExit: Float = 0.0f,
        val logSubpathCount2: Float = 0.0f,
        val logSubpathCount4: Float = 0.0f,
        val logSubpathCount8: Float = 0.0f,
        val logReward: Float = 0.0f,
    )

    @Serializable
    protected data class GlobalStateFeatures( // TODO std
        val averageLogLogicalConstraintsLength: Float = 0.0f,
        val averageLogStateTreeDepth: Float = 0.0f,
        val averageLogStatementRepetitionLocal: Float = 0.0f,
        val averageLogStatementRepetitionGlobal: Float = 0.0f,
        val averageLogDistanceToUncovered: Float = 0.0f,
        val averageLogLastNewDistance: Float = 0.0f,
        val averageLogPathCoverage: Float = 0.0f,
        val averageLogDistanceToBlockEnd: Float = 0.0f,
        val averageLogSubpathCount2: Float = 0.0f,
        val averageLogSubpathCount4: Float = 0.0f,
        val averageLogSubpathCount8: Float = 0.0f,
        val averageLogReward: Float = 0.0f,
        val logFinishedStatesCount: Float = 0.0f,
        val finishedStatesFraction: Float = 0.0f,
        val visitedStatesFraction: Float = 0.0f,
        val totalCoverage: Float = 0.0f,
    )

    @Serializable
    protected data class ActionData(
        val queue: List<StateFeatures>,
        val globalStateFeatures: GlobalStateFeatures,
        val chosenStateId: Int,
        val reward: Float,
        val graphId: Int = 0,
        val blockIds: List<Int>,
        val extraFeatures: List<Float>,
    )

    init {
        File(filepath).mkdirs()
        coverageStatistics.addOnCoveredObserver { _, method, statement ->
            weighter.removeTarget(method, statement)
        }
        jsonStateScheme = buildJsonArray {
            addJsonArray {
                jsonFormat.encodeToJsonElement(StateFeatures()).jsonObject.forEach { t, _ ->
                    add(t)
                }
                jsonFormat.encodeToJsonElement(GlobalStateFeatures()).jsonObject.forEach { t, _ ->
                    add(t)
                }
                if (MainConfig.useGnn) {
                    (0 until MainConfig.gnnFeaturesCount).forEach {
                        add("gnnFeature$it")
                    }
                }
                if (MainConfig.useRnn) {
                    (0 until MainConfig.rnnFeaturesCount).forEach {
                        add("rnnFeature$it")
                    }
                }
            }
            add("chosenStateId")
            add("reward")
            if (MainConfig.logGraphFeatures) {
                add("graphId")
                add("blockIds")
            }
        }
        jsonTrajectoryScheme = buildJsonArray {
            add("hash")
            add("trajectory")
            add("name")
            add("statementsCount")
            if (MainConfig.logGraphFeatures) {
                add("graphFeatures")
                add("graphEdges")
            }
            add("probabilities")
        }
        allStatements = coverageStatistics.getUncoveredStatements().map { it.second }
        method = applicationGraph.methodOf(allStatements.first())
        filename = method.toString().dropWhile { it != ')' }.drop(1)
        val (tmpDistancesToExit, tmpForkCountsToExit) = getDistancesToExit()
        distancesToExit = tmpDistancesToExit
        forkCountsToExit = tmpForkCountsToExit
        minForkCountsToExit = getMinForkCountsToExit()
        blockGraph = BlockGraph(applicationGraph, applicationGraph.entryPoints(method).first(), this)
        graphFeaturesList.add(blockGraph.getGraphFeatures())
        blockGraph.saveGraph(Path(blockGraphsPath, filename, "graph.dot"))
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

    protected open fun getReward(state: State): Float {
        return if (!visitedStatements.contains(state.currentStatement) &&
            allStatements.contains(state.currentStatement)) 1.0f else -penalty
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
        val distanceToUncovered = weighter.weight(state)
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

        return StateFeatures (
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
        val uncoveredStatements = coverageStatistics.getUncoveredStatements().map { it.second }.toSet()

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

    private fun getActionData(stateFeatureQueue: List<StateFeatures>,
                                globalStateFeatures: GlobalStateFeatures,
                                chosenState: State): ActionData {
        val stateId = lru.indexOfFirst { it.id == chosenState.id }
        return ActionData (
            stateFeatureQueue,
            globalStateFeatures,
            stateId,
            getReward(lru[stateId]),
            graphFeaturesList.lastIndex,
            lru.map { it.currentStatement!! }.map { blockGraph.getBlock(it)?.id ?: -1 },
            getExtraFeatures()
        )
    }

    protected fun stateFeaturesToFloatList(stateFeatures: StateFeatures): List<Float> {
        return listOf(
            stateFeatures.logPredecessorsCount,
            stateFeatures.logSuccessorsCount,
            stateFeatures.logCalleesCount,
            stateFeatures.logLogicalConstraintsLength,
            stateFeatures.logStateTreeDepth,
            stateFeatures.logStatementRepetitionLocal,
            stateFeatures.logStatementRepetitionGlobal,
            stateFeatures.logDistanceToUncovered,
            stateFeatures.logLastNewDistance,
            stateFeatures.logPathCoverage,
            stateFeatures.logDistanceToBlockEnd,
            stateFeatures.logDistanceToExit,
            stateFeatures.logForkCount,
            stateFeatures.logStatementFinishCount,
            stateFeatures.logForkCountToExit,
            stateFeatures.logMinForkCountToExit,
            stateFeatures.logSubpathCount2,
            stateFeatures.logSubpathCount4,
            stateFeatures.logSubpathCount8,
            stateFeatures.logReward,
        )
    }

    protected fun globalStateFeaturesToFloatList(globalStateFeatures: GlobalStateFeatures): List<Float> {
        return listOf(
            globalStateFeatures.averageLogLogicalConstraintsLength,
            globalStateFeatures.averageLogStateTreeDepth,
            globalStateFeatures.averageLogStatementRepetitionLocal,
            globalStateFeatures.averageLogStatementRepetitionGlobal,
            globalStateFeatures.averageLogDistanceToUncovered,
            globalStateFeatures.averageLogLastNewDistance,
            globalStateFeatures.averageLogPathCoverage,
            globalStateFeatures.averageLogDistanceToBlockEnd,
            globalStateFeatures.averageLogSubpathCount2,
            globalStateFeatures.averageLogSubpathCount4,
            globalStateFeatures.averageLogSubpathCount8,
            globalStateFeatures.averageLogReward,
            globalStateFeatures.logFinishedStatesCount,
            globalStateFeatures.finishedStatesFraction,
            globalStateFeatures.visitedStatesFraction,
            globalStateFeatures.totalCoverage,
        )
    }

    protected fun blockFeaturesToList(blockFeatures: BlockFeatures): List<Float> {
        return listOf(
            blockFeatures.logLength,
            blockFeatures.logPredecessorsCount,
            blockFeatures.logSuccessorsCount,
            blockFeatures.logTotalCalleesCount,
            blockFeatures.logForkCountToExit,
            blockFeatures.logMinForkCountToExit,
            blockFeatures.isCovered,
        )
    }

    protected open fun getAllFeatures(stateFeatures: StateFeatures, actionData: ActionData, blockId: Int): List<Float> {
        return stateFeaturesToFloatList(stateFeatures) + globalStateFeaturesToFloatList(actionData.globalStateFeatures)
    }

    fun savePath() {
        if (path.isEmpty()) {
            return
        }
        val jsonData = buildJsonObject {
            put("stateScheme", jsonStateScheme)
            put("trajectoryScheme", jsonTrajectoryScheme)
            putJsonArray("path") {
                path.forEach { actionData ->
                    addJsonArray {
                        addJsonArray {
                            actionData.queue.zip(actionData.blockIds).forEach { (stateFeatures, blockId) ->
                                addJsonArray {
                                    getAllFeatures(stateFeatures, actionData, blockId).forEach {
                                        add(it)
                                    }
                                }
                            }
                        }
                        add(actionData.chosenStateId)
                        add(actionData.reward)
                        if (MainConfig.logGraphFeatures) {
                            add(actionData.graphId)
                            addJsonArray {
                                actionData.blockIds.forEach {
                                    add(it)
                                }
                            }
                        }
                    }
                }
            }
            put("statementsCount", allStatements.size)
            if (MainConfig.logGraphFeatures) {
                putJsonArray("graphFeatures") {
                    graphFeaturesList.forEach {  graphFeatures ->
                        addJsonArray {
                            graphFeatures.forEach {nodeFeatures ->
                                addJsonArray {
                                    jsonFormat.encodeToJsonElement(nodeFeatures).jsonObject.forEach { _, u ->
                                        add(u)
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonArray("graphEdges") {
                    blockGraph.getEdges().toList().forEach { nodeList ->
                        addJsonArray {
                            nodeList.forEach {
                                add(it)
                            }
                        }
                    }
                }
            }
            putJsonArray("probabilities") {
                probabilities.forEach { queueProbabilities ->
                    addJsonArray {
                        queueProbabilities.forEach { probability ->
                            add(probability)
                        }
                    }
                }
            }
        }
        Path(filepath, "$filename.json").toFile()
            .writeText(jsonFormat.encodeToString(jsonData))
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

        if (coverageStatistics.getUncoveredStatements().map { it.second }.contains(statement)) {
            stateLastNewStatement[state] = statePath.size
            statePathCoverage[state] = statePathCoverage.getValue(state) + 1u
        }
    }

    protected open fun getNodeName(node: PathsTrieNode<State, Statement>, id: Int): String {
        val statement = try {
            node.statement
        } catch (e: UnsupportedOperationException) {
            "No Statement"
        }
        var name = "\"$id: $statement"
        node.states.forEach { state ->
            name += ", ${state.id}"
        }
        name += "\""
        return name
    }

    private fun saveGraph() {
        val nodes = mutableListOf<PathsTrieNode<State, Statement>>()
        val treeQueue = ArrayDeque<PathsTrieNode<State, Statement>>()
        treeQueue.add(pathsTreeRoot)
        while (treeQueue.isNotEmpty()) {
            val currentNode = treeQueue.removeFirst()
            nodes.add(currentNode)
            treeQueue.addAll(currentNode.children.values)
        }
        val nodeNames = nodes.zip(nodes.indices).associate { (node, id) ->
            Pair(node, getNodeName(node, id))
        }.withDefault { "" }
        val graph = digraph ("step$stepCount") {
            nodes.forEach { node ->
                val nodeName = nodeNames.getValue(node)
                +nodeName
                node.children.values.forEach { child ->
                    val childName = nodeNames.getValue(child)
                    nodeName - childName
                }
            }
        }
        stepCount += 1
        val path = Path(graphsPath, filename, "${graph.name}.dot")
        path.parent.toFile().mkdirs()
        path.writeText(graph.dot())
    }

    protected fun beforePeek(): Pair<List<StateFeatures>, GlobalStateFeatures> {
        if (MainConfig.graphUpdate == GraphUpdate.TestGeneration && (path.lastOrNull()?.reward ?: 0.0f) > 0.5f) {
            graphFeaturesList.add(blockGraph.getGraphFeatures())
        }
        val stateFeatureQueue = getStateFeatureQueue()
        return Pair(stateFeatureQueue, getGlobalStateFeatures(stateFeatureQueue))
    }

    protected fun afterPeek(state: State,
                            stateFeatureQueue: List<StateFeatures>,
                            globalStateFeatures: GlobalStateFeatures) {
        val actionData = getActionData(stateFeatureQueue, globalStateFeatures, state)
        path.add(actionData)
//        savePath()
        updateCoverage(state)
        if (stepCount < 100) {
            saveGraph()
        }
        lru.remove(state)
        lru.add(state)
    }

    override fun isEmpty() = lru.isEmpty()

    override fun peek(): State {
        val (stateFeatureQueue, globalStateFeatures) = beforePeek()
        val state = queue.first()
        afterPeek(state, stateFeatureQueue, globalStateFeatures)
        return state
    }

    override fun update(state: State) {}

    override fun add(states: Collection<State>) {
        if (states.isEmpty()) {
            return
        }
        queue.addAll(states)
        lru.addAll(states)
        allStatesCount += states.size.toUInt()
    }

    override fun remove(state: State) {
        when (state) {
            queue.last() -> queue.removeLast() // fast remove from the tail
            queue.first() -> queue.removeFirst() // fast remove from the head
            else -> queue.remove(state)
        }
        lru.remove(state)
        finishedStatesCount += 1u
        state.reversedPath.asSequence().toSet().forEach {  statement ->
            statementFinishCounts[statement] = statementFinishCounts.getValue(statement) + 1u
        }
    }
}
