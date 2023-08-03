package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.Postprocessing
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
    private var outputValues = listOf<Float>()
    private var chosenStateId = 0
    private val random = Random(java.time.LocalDateTime.now().nano)

    companion object {
        private val modelPath = Path(MainConfig.gameEnvPath, "model.onnx").toString()
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
        return "\"$id: ${DecimalFormat("0.00E0").format(outputValues.getOrElse(stateId) { -1.0f })}, " +
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
        val allFeaturesListFull = stateFeatureQueue.map { stateFeatures ->
            stateFeaturesToFloatList(stateFeatures) + globalFeaturesList
        }
        val firstIndex = if (MainConfig.maxAttentionLength == -1) 0 else
            maxOf(0, queue.size - MainConfig.maxAttentionLength)
        val allFeaturesList = allFeaturesListFull.subList(firstIndex, queue.size)
        val totalSize = allFeaturesList.size * allFeaturesList.first().size
        val totalKnownSize = MainConfig.inputShape.reduce { acc, l -> acc * l }
        val shape = MainConfig.inputShape.map { if (it != -1L) it else -totalSize / totalKnownSize }.toLongArray()
        val dataBuffer = FloatBuffer.allocate(totalSize)
        allFeaturesList.forEach { stateFeatures ->
            stateFeatures.forEach { feature ->
                dataBuffer.put(feature)
            }
        }
        dataBuffer.rewind()
        val data = OnnxTensor.createTensor(env, dataBuffer, shape)
        val result = session!!.run(mapOf(Pair("input", data)))
        val output = (result.get("output").get().value as Array<*>).flatMap { (it as FloatArray).toList() }
        chosenStateId = firstIndex + when (MainConfig.postprocessing) {
            Postprocessing.Argmax -> {
                output.indices.maxBy { output[it] }
            }
            Postprocessing.Softmax -> {
                val exponents = output.map { exp(it) }
                val exponentsSum = exponents.sum()
                chooseRandomId(exponents.map { it / exponentsSum })
            }
            else -> {
                chooseRandomId(output)
            }
        }
        outputValues = List(firstIndex) { -1.0f } + output
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
