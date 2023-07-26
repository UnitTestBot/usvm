package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.kinference.core.KIEngine
import io.kinference.core.KIONNXData
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.model.Model
import io.kinference.ndarray.arrays.FloatNDArray
import kotlinx.coroutines.runBlocking
import org.usvm.Algorithm
import org.usvm.MainConfig
import org.usvm.UState
import org.usvm.statistics.*
import java.io.File
import java.nio.FloatBuffer
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.math.exp
import kotlin.random.Random

internal open class InferencePathSelector<State : UState<*, *, Method, Statement>, Statement, Method> (
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) : BfsWithLoggingPathSelector<State, Statement, Method>(
    pathsTreeStatistics,
    coverageStatistics,
    distanceStatistics,
    applicationGraph
) {
    private var qValues = listOf<Float>()
    private var chosenStateId = 0
    private val random = Random(java.time.LocalDateTime.now().nano)

    companion object {
        private val modelPath = Path(MainConfig.gameEnvPath, "model.onnx").toString()
        private var model: Model<KIONNXData<*>>? = null
        private var env: OrtEnvironment? = null
        private var session: OrtSession? = null
    }

    override fun getReward(state: State): Float {
        val statement = state.currentStatement
        if (statement === null ||
            (applicationGraph.successors(statement).toList().size +
             applicationGraph.callees(statement).toList().size != 0) ||
            applicationGraph.methodOf(statement) != method ||
            state.callStack.size != 1) {
            return 0.0f
        }
        return coverageStatistics.getUncoveredStatements().map { it.second }.toSet()
            .intersect(state.path.toSet()).size.toFloat()
    }

    override fun getNodeName(node: PathsTreeNode<State>, id: Int): String {
        val state = node.state
        if (state === null) {
            return super.getNodeName(node, id)
        }
        val stateId = queue.indexOf(state)
        if (stateId == -1) {
            return super.getNodeName(node, id)
        }
        return "\"$id: Q=${DecimalFormat("0.00E0").format(qValues.getOrElse(stateId) { -1.0f })}, " +
                "${state.currentStatement}\""
    }

    private fun stateFeaturesToFloatList(stateFeatures: StateFeatures): List<Float> {
        return listOf(
            stateFeatures.logSuccessorsCount,
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

    private fun globalStateFeaturesToFloatList(globalStateFeatures: GlobalStateFeatures): List<Float> {
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

    private fun chooseRandomId(probabilities: Collection<Float>): Int {
        val randomNumber = random.nextFloat()
        var probability = 0.0f
        probabilities.withIndex().forEach {
            probability += it.value
            if (randomNumber < probability) {
                return it.index
            }
        }
        return probabilities.size - 1
    }

    protected fun peekWithKInference(stateFeatureQueue: List<StateFeatures>?,
                                     globalStateFeatures: GlobalStateFeatures?) : State {
        if (stateFeatureQueue == null || globalStateFeatures == null) {
            throw IllegalArgumentException("No features")
        }
        if (model === null) {
            model = runBlocking { Model.load(File(modelPath).readBytes(), KIEngine) }
        }
        val globalFeaturesList = globalStateFeaturesToFloatList(globalStateFeatures)
        val allFeaturesList = stateFeatureQueue.map { stateFeatures ->
            stateFeaturesToFloatList(stateFeatures) + globalFeaturesList
        }
        val shape = intArrayOf(allFeaturesList.size, allFeaturesList.first().size)
        val data = FloatNDArray(shape) { i ->
            allFeaturesList[i / shape[1]][i % shape[1]]
        }.asTensor("input")
        val output = runBlocking {
            (model!!.predict(listOf(data))["output"] as KITensor).data as FloatNDArray
        }.array.toArray()
        chosenStateId = if (MainConfig.algorithm == Algorithm.TD) {
            output.indices.maxBy { output[it] }
        } else {
            val exponents = output.map { exp(it) }
            val exponentsSum = exponents.sum()
            chooseRandomId(exponents.map { it / exponentsSum })
        }
        return queue[chosenStateId]
    }

    private fun peekWithOnnxRuntime(stateFeatureQueue: List<StateFeatures>?,
                                    globalStateFeatures: GlobalStateFeatures?) : State {
        if (stateFeatureQueue == null || globalStateFeatures == null) {
            throw IllegalArgumentException("No features")
        }
        if (env === null || session === null) {
            env = OrtEnvironment.getEnvironment()
            session = env!!.createSession(modelPath, OrtSession.SessionOptions())
        }
        val globalFeaturesList = globalStateFeaturesToFloatList(globalStateFeatures)
        val allFeaturesList = stateFeatureQueue.map { stateFeatures ->
            stateFeaturesToFloatList(stateFeatures) + globalFeaturesList
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
        val globalStateFeatures = getGlobalStateFeatures(stateFeatureQueue)
        val state = if (File(modelPath).isFile) {
            peekWithOnnxRuntime(stateFeatureQueue, globalStateFeatures)
        } else {
            queue.first()
        }
        afterPeek(state, stateFeatureQueue, globalStateFeatures)
        return state
    }
}
