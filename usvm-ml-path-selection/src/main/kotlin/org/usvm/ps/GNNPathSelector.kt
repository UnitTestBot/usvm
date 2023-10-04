package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.ApplicationBlockGraph
import org.usvm.StateId
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StateVisitsStatistics
import java.nio.FloatBuffer
import java.nio.LongBuffer

data class GameEdgeLabel(
    val token: Int
)

data class GameMapEdge(
    val vertexFrom: Int,
    val vertexTo: Int,
    val label: GameEdgeLabel = GameEdgeLabel(0),
)

data class BlockFeatures(
    val uid: Int = 0,
    val id: Int,
    val basicBlockSize: Int,
    val inCoverageZone: Boolean,
    val coveredByTest: Boolean,
    val visitedByState: Boolean,
    val touchedByState: Boolean,
    val states: List<UInt>,
)

data class StateHistoryElem(
    val graphVertexId: Int,
    val numOfVisits: Int,
)

data class StateFeatures(
    val id: StateId,
    val position: Int = 0,
    val predictedUsefulness: Int = 42,
    val pathConditionSize: Int,
    val visitedAgainVertices: Int,
    val visitedNotCoveredVerticesInZone: Int,
    val visitedNotCoveredVerticesOutOfZone: Int,
    val history: List<StateHistoryElem>,
    val children: List<UInt>,
)

data class GraphNative(
    val gameVertex: List<List<Int>>,
    val stateVertex: List<List<Int>>,
    val gameVertexToGameVertex: List<List<Int>>,
    val gameVertexHistoryStateVertexIndex: List<List<Int>>,
    val gameVertexHistoryStateVertexAttrs: List<Int>,
    val gameVertexInStateVertex: List<List<Int>>,
    val stateVertexParentOfStateVertex: List<List<Int>>,
    val stateMap: Map<Int, Int>
)

class GNNPathSelector<Method, BasicBlock, Statement, State : UState<*, Method, Statement, *, *, State>>(
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    private val stateVisitsStatistics: StateVisitsStatistics<Method, Statement, State>,
    private val applicationBlockGraph: ApplicationBlockGraph<Method, BasicBlock, Statement>,
    heteroGNNModelPath: String,
) : UPathSelector<State> {
    private val states: MutableList<State> = mutableListOf()

    private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var gnnSession: OrtSession = env.createSession(heteroGNNModelPath)

    override fun isEmpty() = states.isEmpty()
    override fun peek(): State {
        val nativeInput = createNativeInput()
        if (nativeInput.gameVertexInStateVertex.isEmpty()) {
            return states.first()
        }

        val gameVertexTensor = onnxFloatTensor(nativeInput.gameVertex)
        val stateVertexTensor = onnxFloatTensor(nativeInput.stateVertex)
        val gameVertexToGameVertexTensor = onnxLongTensor(nativeInput.gameVertexToGameVertex.transpose())
        val gameVertexHistoryStateVertexIndexTensor =
            onnxLongTensor(nativeInput.gameVertexHistoryStateVertexIndex.transpose())
        val gameVertexHistoryStateVertexAttrsTensor =
            onnxLongTensor(nativeInput.gameVertexHistoryStateVertexAttrs.map { listOf(it) })  // this rn can be of shape [0, 0], we need to fix this

        val gameVertexInStateVertexTensor = onnxLongTensor(nativeInput.gameVertexInStateVertex.transpose())
        val stateVertexParentOfStateVertexTensor =
            onnxLongTensor(nativeInput.stateVertexParentOfStateVertex.transpose())

        val res = gnnSession.run(
            mapOf(
                "game_vertex" to gameVertexTensor,
                "state_vertex" to stateVertexTensor,
                "game_vertex to game_vertex" to gameVertexToGameVertexTensor,
                "game_vertex history state_vertex index" to gameVertexHistoryStateVertexIndexTensor,
                "game_vertex history state_vertex attrs" to gameVertexHistoryStateVertexAttrsTensor,
                "game_vertex in state_vertex" to gameVertexInStateVertexTensor,
                "state_vertex parent_of state_vertex" to stateVertexParentOfStateVertexTensor
            )
        )

        val predictedStatesRanks =
            (res["out"].get().value as Array<*>).map { it as FloatArray }.map { it.toList() }.toList()
        val chosenStateId = predictState(predictedStatesRanks, nativeInput.stateMap)

        return states.single { state -> state.id.toInt() == chosenStateId }
    }

    override fun update(state: State) {}

    override fun add(states: Collection<State>) {
        this.states += states
    }

    override fun remove(state: State) {
        states.remove(state)
    }

    private fun createNativeInput(): GraphNative {
        val nodesState = mutableListOf<List<Int>>()
        val nodesVertex = mutableListOf<List<Int>>()
        val edgesIndexVSHistory = mutableListOf<List<Int>>()
        val edgesAttrVS = mutableListOf<Int>()
        val edgesIndexSS = mutableListOf<List<Int>>()
        val edgesIndexVSIn = mutableListOf<List<Int>>()

        val stateMap = mutableMapOf<Int, Int>()
        val vertexMap = mutableMapOf<BasicBlock, Int>()

        val blockIdGenerator = IDGenerator()
        val rawBlocks = applicationBlockGraph.blocks()

        val blocksFeatures = mutableListOf<BlockFeatures>()

        rawBlocks.forEach { block ->
            val blockId = blockIdGenerator.issue()
            vertexMap[block] = blockId
            blocksFeatures.add(
                createBlockFeatures(
                    id = blockId,
                    block = block
                )
            )
        }

        val statesFeatures =
            states.map { state -> getStateFeatures(state, rawBlocks) { vertexMap[it]!! } }
        for ((stateIndexOrder, stateFeatures) in statesFeatures.withIndex()) {
            stateMap[stateFeatures.id.toInt()] = stateIndexOrder
            nodesState.add(stateFeatures.toList())
        }

        blocksFeatures.forEach { blockFeatures ->
            nodesVertex.add(blockFeatures.toList())
        }

        val edgesIndexVV = applicationBlockGraph
            .edges(rawBlocks) { bb -> vertexMap[bb]!! }
            .map { edge -> listOf(edge.vertexFrom, edge.vertexTo) }
            .toList()

        for ((stateIndexOrder, stateFeatures) in statesFeatures.withIndex()) {
            for (historyEdge in stateFeatures.history) {
                val vertexTo = historyEdge.graphVertexId
                edgesIndexVSHistory.add(listOf(vertexTo, stateIndexOrder))
                edgesAttrVS.add(historyEdge.numOfVisits)
            }
        }

        for (stateFeatures in statesFeatures) {
            for (childId in stateFeatures.children) {
                if (childId.toInt() in stateMap.keys)
                    edgesIndexSS.add(listOf(stateMap[stateFeatures.id.toInt()]!!, stateMap[childId.toInt()]!!))
            }
        }

        for (vertex in blocksFeatures) {
            for (state in vertex.states) {
                edgesIndexVSIn.add(listOf(vertex.id, stateMap[state.toInt()]!!))
            }
        }

        return GraphNative(
            gameVertex = nodesVertex,
            stateVertex = nodesState,
            gameVertexToGameVertex = edgesIndexVV,
            gameVertexHistoryStateVertexIndex = edgesIndexVSHistory,
            gameVertexHistoryStateVertexAttrs = edgesAttrVS,
            gameVertexInStateVertex = edgesIndexVSIn,
            stateVertexParentOfStateVertex = edgesIndexSS,
            stateMap = stateMap
        )
    }

    private fun <T> get2DShape(data: List<List<T>>): Pair<Int, Int> {
        if (data.isEmpty()) {
            return Pair(0, 0)
        }
        if (data[0].isEmpty()) {
            return Pair(data.size, 0)
        }
        return Pair(data.size, data[0].size)
    }


    private fun List<Float>.create2DFloatBuffer(shape: Pair<Int, Int>): OnnxTensor {
        val longArrayOfShape = longArrayOf(shape.first.toLong(), shape.second.toLong())
        return OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(this.toFloatArray()),
            longArrayOfShape
        )
    }

    private fun List<Long>.create2DLongBuffer(shape: Pair<Int, Int>): OnnxTensor {
        val longArrayOfShape = longArrayOf(shape.first.toLong(), shape.second.toLong())
        return OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(this.toLongArray()),
            longArrayOfShape
        )
    }

    private fun onnxFloatTensor(data: List<List<Int>>): OnnxTensor {
        return data.flatten().map { it.toFloat() }.create2DFloatBuffer(get2DShape(data))
    }

    private fun onnxLongTensor(data: List<List<Int>>): OnnxTensor {
        return data.flatten().map { it.toLong() }.create2DLongBuffer(get2DShape(data))
    }

    private fun BasicBlock.inCoverageZone() = coverageStatistics.inCoverageZone(applicationBlockGraph.methodOf(this))

    private fun BasicBlock.isVisited() = stateVisitsStatistics.isVisited(this.instructions().last())

    private fun BasicBlock.isTouched() =
        this.isVisited() || stateVisitsStatistics.isVisited(this.instructions().first())

    private fun BasicBlock.isCovered() = coverageStatistics.isCovered(this.instructions().first())

    private fun createBlockFeatures(
        id: Int,
        block: BasicBlock
    ): BlockFeatures {
        return BlockFeatures(
            id = id,
            inCoverageZone = block.inCoverageZone(),
            basicBlockSize = block.instructions().count(),
            coveredByTest = block.isCovered(),
            visitedByState = block.isVisited(),
            touchedByState = block.isTouched(),
            states = states
                .filter { it.currentStatement in applicationBlockGraph.instructions(block) }
                .map { it.id }
        )
    }

    private fun BasicBlock.instructions(): Sequence<Statement> {
        return applicationBlockGraph.instructions(this)
    }

    private fun getStateFeatures(
        state: State,
        blocksSource: Sequence<BasicBlock>,
        mapper: (BasicBlock) -> Int
    ): StateFeatures {
        val blockHistory = state.reversedPathFrom(blocksSource).toList().reversed()

        val visitedNotCoveredVerticesInZone = blockHistory.count { it.isVisited() && it.inCoverageZone() }
        val visitedNotCoveredVerticesOutOfZone = blockHistory.count { it.isVisited() && !it.inCoverageZone() }

        return StateFeatures(
            id = state.id,
            pathConditionSize = state.pathConstraints.size(),
            visitedAgainVertices = state.reversedPath.asSequence().count() - state.reversedPath.asSequence().distinct()
                .count(),
            visitedNotCoveredVerticesInZone = visitedNotCoveredVerticesInZone,
            visitedNotCoveredVerticesOutOfZone = visitedNotCoveredVerticesOutOfZone,
            history = blockHistory.map { block ->
                StateHistoryElem(
                    mapper(block),
                    blockHistory.count { other -> mapper(block) == mapper(other) })
            },
            children = state.pathLocation.accumulatedForks.map { it.id }
        )
    }

    private fun UState<*, *, Statement, *, *, State>.reversedPathFrom(sourceBlocks: Sequence<BasicBlock>): Sequence<BasicBlock> {
        val blocks = mutableSetOf<BasicBlock>()
        for (instruction in this.reversedPath) {
            if (instruction in sourceBlocks.map { block -> applicationBlockGraph.instructions(block) }.flatten())
                blocks.add(applicationBlockGraph.blockOf(instruction))
            // else: external call
        }
        return blocks.asSequence()
    }

    fun <Method, BasicBlock, Statement> ApplicationBlockGraph<Method, BasicBlock, Statement>.edges(
        rawBlocks: Sequence<BasicBlock>,
        mapper: (BasicBlock) -> Int
    ): Sequence<GameMapEdge> {
        return rawBlocks.flatMap { basicBlock ->
            this.successors(basicBlock).map { otherBasicBlock ->
                GameMapEdge(mapper(basicBlock), mapper(otherBasicBlock))
            }
        }
    }
}

fun predictState(stateRank: List<List<Float>>, stateMap: Map<Int, Int>): Int {
    val reverseStateMap = stateMap.entries.associate { (k, v) -> v to k }

    val stateRankMapping = stateRank.mapIndexed { orderIndex, rank ->
        reverseStateMap[orderIndex]!! to rank
    }

    return stateRankMapping.maxBy { it.second.sum() }.first
}

fun BlockFeatures.toList(): List<Int> {
    return listOf(
        this.inCoverageZone.toInt(),
        this.basicBlockSize,
        this.coveredByTest.toInt(),
        this.visitedByState.toInt(),
        this.touchedByState.toInt()
    )
}

fun StateFeatures.toList(): List<Int> {
    return listOf(
        this.position,
        this.predictedUsefulness,
        this.pathConditionSize,
        this.visitedAgainVertices,
        this.visitedNotCoveredVerticesInZone,
        this.visitedNotCoveredVerticesOutOfZone
    )
}
