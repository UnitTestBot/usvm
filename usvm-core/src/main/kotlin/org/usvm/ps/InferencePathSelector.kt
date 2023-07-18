package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.rchowell.dotlin.digraph
import io.kinference.core.KIEngine
import io.kinference.core.KIONNXData
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.model.Model
import io.kinference.ndarray.arrays.FloatNDArray
import kotlinx.coroutines.runBlocking
import org.usvm.UState
import org.usvm.statistics.*
import java.io.File
import java.nio.FloatBuffer
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.io.path.writeText

internal open class InferencePathSelector<State : UState<*, *, Method, Statement>, Statement, Method> (
    private val pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>
) : BfsWithLoggingPathSelector<State, Statement, Method>(
    pathsTreeStatistics,
    coverageStatistics,
    distanceStatistics,
    applicationGraph
) {
    private val modelPath = "../Game_env/model.onnx"
    private var model: Model<KIONNXData<*>>? = null
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    private val graphsPath = "../Game_env/graphs/"
    private var qValues = listOf<Float>()
    private var stepCount = 0

    protected var chosenStateId = 0

    private fun stateFeaturesToFloatList(stateFeatures: StateFeatures): List<Float> {
        return listOf(
            stateFeatures.successorsCount.toFloat(),
            stateFeatures.finishedStatesCount.toFloat(),
            stateFeatures.logicalConstraintsLength.toFloat(),
            stateFeatures.stateTreeDepth.toFloat(),
            stateFeatures.statementRepetitionLocal.toFloat(),
            stateFeatures.statementRepetitionGlobal.toFloat(),
            stateFeatures.distanceToUncovered,
            stateFeatures.lastNewDistance.toFloat(),
            stateFeatures.pathCoverage.toFloat(),
            stateFeatures.reward
        )
    }

    private fun getNodeName(node: PathsTreeNode<State>, id: Int): String {
        if (node.state === null) {
            return "\"$id: null\""
        }
        val stateId = queue.indexOf(node.state)
        if (stateId == -1) {
            return "\"$id: fin\""
        }
        return "\"$id: Q=${DecimalFormat("0.00E0").format(qValues[stateId])}\""
    }

    private fun saveGraph() {
        val nodes = mutableListOf<PathsTreeNode<State>>()
        val treeQueue = ArrayDeque<PathsTreeNode<State>>()
        treeQueue.add(pathsTreeStatistics.root)
        while (treeQueue.isNotEmpty()) {
            val currentNode = treeQueue.removeFirst()
            nodes.add(currentNode)
            treeQueue.addAll(currentNode.children)
        }
        val nodeNames = nodes.zip(nodes.indices).associate { (node, id) ->
            Pair(node, getNodeName(node, id))
        }.withDefault { "" }
        val graph = digraph ("step$stepCount") {
            nodes.forEach { node ->
                val nodeName = nodeNames.getValue(node)
                +nodeName
                node.children.forEach { child ->
                    val childName = nodeNames.getValue(child)
                    nodeName - childName
                }
            }
        }
        stepCount += 1
        val path = Path(graphsPath, "${graph.name}.dot")
        path.parent.toFile().mkdirs()
        path.writeText(graph.dot())
    }

    private fun averageStateFeaturesToFloatList(averageStateFeatures: AverageStateFeatures): List<Float> {
        return listOf(
            averageStateFeatures.averageSuccessorsCount,
            averageStateFeatures.averageLogicalConstraintsLength,
            averageStateFeatures.averageStateTreeDepth,
            averageStateFeatures.averageStatementRepetitionLocal,
            averageStateFeatures.averageStatementRepetitionGlobal,
            averageStateFeatures.averageDistanceToUncovered,
            averageStateFeatures.averageLastNewDistance,
            averageStateFeatures.averagePathCoverage,
            averageStateFeatures.averageReward
        )
    }

    protected fun peekWithKInference(stateFeatureQueue: List<StateFeatures>?,
                                   averageStateFeatures: AverageStateFeatures?) : State {
        if (stateFeatureQueue == null || averageStateFeatures == null) {
            throw IllegalArgumentException("No features")
        }
        if (model === null) {
            model = runBlocking { Model.load(File(modelPath).readBytes(), KIEngine) }
        }
        val averageFeaturesList = averageStateFeaturesToFloatList(averageStateFeatures)
        val allFeaturesList = stateFeatureQueue.map { stateFeatures ->
            stateFeaturesToFloatList(stateFeatures) + averageFeaturesList
        }
        val shape = intArrayOf(allFeaturesList.size, allFeaturesList.first().size)
        val data = FloatNDArray(shape) { i ->
            allFeaturesList[i / shape[1]][i % shape[1]]
        }.asTensor("input")
        val output = runBlocking {
            (model!!.predict(listOf(data))["output"] as KITensor).data as FloatNDArray
        }.array.toArray()
        chosenStateId = output.indices.maxBy { output[it] }
        return queue[chosenStateId]
    }

    protected fun peekWithOnnxRuntime(stateFeatureQueue: List<StateFeatures>?,
                                    averageStateFeatures: AverageStateFeatures?) : State {
        if (stateFeatureQueue == null || averageStateFeatures == null) {
            throw IllegalArgumentException("No features")
        }
        if (env === null || session === null) {
            env = OrtEnvironment.getEnvironment()
            session = env!!.createSession(modelPath, OrtSession.SessionOptions())
        }
        val averageFeaturesList = averageStateFeaturesToFloatList(averageStateFeatures)
        val allFeaturesList = stateFeatureQueue.map { stateFeatures ->
            stateFeaturesToFloatList(stateFeatures) + averageFeaturesList
        }
        val shape = longArrayOf(allFeaturesList.size.toLong(), allFeaturesList.first().size.toLong())
        val dataBuffer = FloatBuffer.allocate(allFeaturesList.size * allFeaturesList.first().size)
        allFeaturesList.forEach { stateFeatures ->
            stateFeatures.forEach { feature ->
                dataBuffer.put(feature)
            }
        }
        dataBuffer.rewind()
        val data = OnnxTensor.createTensor(env, dataBuffer, shape)
        val result = session!!.run(mapOf(Pair("input", data)))
        val output = (result.get("output").get().value as Array<*>)
            .asList().map { (it as FloatArray)[0] }
        qValues = output
        chosenStateId = output.indices.maxBy { output[it] }
        return queue[chosenStateId]
    }

    override fun peek(): State {
        val stateFeatureQueue = getStateFeatureQueue()
        val averageStateFeatures = getAverageStateFeatures(stateFeatureQueue)
        val state = peekWithOnnxRuntime(stateFeatureQueue, averageStateFeatures)
        path.add(getActionData(stateFeatureQueue, averageStateFeatures, state))
        saveGraph()
        savePath()
        updateCoverage(state)
        return state
    }
}
