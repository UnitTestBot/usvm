package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.UState
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.collections.set
import kotlin.io.path.Path

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

open class GNNPathSelector<State : UState<*, Method, Statement, *, *, State>, Statement, Method>(
    applicationGraph: ApplicationGraph<Method, Statement>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
) : BlockGraphPathSelector<State, Statement, Method>(
    coverageStatistics,
    applicationGraph
) {
    companion object {
        private val gnnModelPath = Path("/Users/emax/Data/usvm/Game_env/test_model.onnx").toString()
        private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
        private var gnnSession: OrtSession = env.createSession(gnnModelPath)
    }

    fun coverage(): Float {
        return coverageStatistics.getTotalCoverage()
    }

    override fun peek(): State {
        val nativeInput = createNativeInput()

        val gameVertexTensor = onnxFloatTensor(nativeInput.gameVertex)
        val stateVertexTensor = onnxFloatTensor(nativeInput.stateVertex)
        val gameVertexToGameVertexTensor = onnxLongTensor(nativeInput.gameVertexToGameVertex.transpose())
        val gameVertexHistoryStateVertexIndexTensor =
            onnxLongTensor(nativeInput.gameVertexHistoryStateVertexIndex.transpose())
        val gameVertexHistoryStateVertexAttrsTensor =
            onnxLongTensor(nativeInput.gameVertexHistoryStateVertexAttrs.map { listOf(it) })
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

    private fun createNativeInput(): GraphNative {
        val nodesState = mutableListOf<List<Int>>()
        val nodesVertex = mutableListOf<List<Int>>()
        val edgesIndexVSHistory = mutableListOf<List<Int>>()
        val edgesAttrVS = mutableListOf<Int>()
        val edgesIndexSS = mutableListOf<List<Int>>()
        val edgesIndexVSIn = mutableListOf<List<Int>>()

        val stateMap = mutableMapOf<Int, Int>()
        val vertexMap = mutableMapOf<Int, Int>()

        val statesFeatures = states.map { getStateFeatures(it) }

        for ((stateIndexOrder, stateFeatures) in statesFeatures.withIndex()) {
            stateMap[stateFeatures.id.toInt()] = stateIndexOrder
            nodesState.add(stateFeatures.toList())
        }

        for ((vertexIndexOrder, vertex) in blockGraph.getVertices().withIndex()) {
            vertexMap[vertex.id] = vertexIndexOrder
            val blockFeatures = blockGraph.getBlockFeatures(
                block = vertex,
                isCovered = ::isCovered,
                inCoverageZone = ::inCoverageZone,
                isVisited = ::isVisited,
                stateIdsInBlock = states.filter { it.currentStatement in vertex.path }.map { it.id }
            )
            nodesVertex.add(blockFeatures.toList())
        }

        val edgesIndexVV = blockGraph.getEdges().map { listOf(vertexMap[it.vertexFrom]!!, vertexMap[it.vertexTo]!!) }

        for ((stateIndexOrder, stateFeatures) in statesFeatures.withIndex()) {
            for (historyEdge in stateFeatures.history) {
                val vertexTo = vertexMap[historyEdge.graphVertexId]!!
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

        for (vertex in blockGraph.getVertices()) {
            for (state in states) {
                edgesIndexVSIn.add(listOf(vertexMap[vertex.id]!!, stateMap[state.id.toInt()]!!))
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
}

private fun predictState(stateRank: List<List<Float>>, stateMap: Map<Int, Int>): Int {
    val reverseStateMap = stateMap.entries.associate { (k, v) -> v to k }

    val stateRankMapping = stateRank.mapIndexed { orderIndex, rank ->
        reverseStateMap[orderIndex]!! to rank
    }

    return stateRankMapping.maxBy { it.second.sum() }.first
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

private fun <T> List<List<T>>.transpose(): List<List<T>> {
    if (this.isEmpty()) {
        return listOf(listOf(), listOf())
    }

    val (rows, cols) = get2DShape(this)
    return List(cols) { j ->
        List(rows) { i ->
            this[i][j]
        }
    }
}

private fun Boolean.toInt(): Int = if (this) 1 else 0

private fun BlockFeatures.toList(): List<Int> {
    return listOf(
        this.inCoverageZone.toInt(),
        this.basicBlockSize,
        this.coveredByTest.toInt(),
        this.visitedByState.toInt(),
        this.touchedByState.toInt()
    )
}

private fun StateFeatures.toList(): List<Int> {
    return listOf(
        this.position,
        this.predictedUsefulness,
        this.pathConditionSize,
        this.visitedAgainVertices,
        this.visitedNotCoveredVerticesInZone,
        this.visitedNotCoveredVerticesOutOfZone
    )
}
