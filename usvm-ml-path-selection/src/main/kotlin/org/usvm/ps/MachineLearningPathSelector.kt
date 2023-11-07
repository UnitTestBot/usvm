package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.*
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.util.prod
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.math.exp
import kotlin.random.Random

open class MachineLearningPathSelector<State : UState<*, Method, Statement, *, *, State>, Statement, Method>(
    pathsTreeRoot: PathsTrieNode<State, Statement>,
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    cfgStatistics: CfgStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    private val mlConfig: MLConfig,
    private val defaultPathSelector: UPathSelector<State>
) : FeaturesLoggingPathSelector<State, Statement, Method>(
    pathsTreeRoot,
    coverageStatistics,
    cfgStatistics,
    applicationGraph,
    mlConfig,
    defaultPathSelector,
) {
    private var outputValues = listOf<Float>()
    private val random = Random(System.nanoTime())
    private val gnnFeaturesList = mutableListOf<List<List<Float>>>()
    private var lastStateFeatures = List(mlConfig.rnnStateShape.prod().toInt()) { 0.0f }
    private var rnnFeatures = if (mlConfig.useRnn) List(mlConfig.rnnFeaturesCount) { 0.0f } else emptyList()

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val actorModelPath = Path(mlConfig.gameEnvPath, "actor_model.onnx").toString()
    private val gnnModelPath = Path(mlConfig.gameEnvPath, "gnn_model.onnx").toString()
    private val rnnModelPath = Path(mlConfig.gameEnvPath, "rnn_cell.onnx").toString()
    private var actorSession: OrtSession? = if (File(actorModelPath).isFile)
        env.createSession(actorModelPath) else null
    private var gnnSession: OrtSession? = if (mlConfig.useGnn)
        env.createSession(gnnModelPath) else null
    private var rnnSession: OrtSession? = if (mlConfig.useRnn)
        env.createSession(rnnModelPath) else null

    override fun getExtraNodeInfo(node: PathsTrieNode<State, Statement>) =
        node.states.joinToString(separator = "") { state ->
            ", ${DecimalFormat("0.00E0").format(outputValues.getOrElse(lru.indexOf(state)) { -1.0f })}"
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

    private fun runGnn(): List<List<Float>> {
        if (gnnFeaturesList.size == graphFeaturesList.size) {
            return gnnFeaturesList.last()
        }
        if (gnnSession === null) {
            gnnSession = env.createSession(gnnModelPath, OrtSession.SessionOptions())
        }
        val graphFeatures = graphFeaturesList.last().map { blockFeaturesToList(it) }
        val graphEdges = blockGraph.getEdges().toList()
        val featuresShape = listOf(graphFeatures.size, graphFeatures.first().size)
        val edgesShape = listOf(2, graphEdges.first().size)
        val featuresDataBuffer = FloatBuffer.allocate(featuresShape.prod())
        graphFeatures.forEach { blockFeatures ->
            blockFeatures.forEach { feature ->
                featuresDataBuffer.put(feature)
            }
        }
        featuresDataBuffer.rewind()
        val edgesDataBuffer = LongBuffer.allocate(edgesShape.prod())
        graphEdges.forEach { nodes ->
            nodes.forEach { node ->
                edgesDataBuffer.put(node.toLong())
            }
        }
        edgesDataBuffer.rewind()
        val featuresData = OnnxTensor.createTensor(
            env, featuresDataBuffer,
            featuresShape.map { it.toLong() }.toLongArray()
        )
        val edgesData = OnnxTensor.createTensor(
            env, edgesDataBuffer,
            edgesShape.map { it.toLong() }.toLongArray()
        )
        val result = gnnSession!!.run(mapOf(Pair("x", featuresData), Pair("edge_index", edgesData)))
        val output = (result.get("output").get().value as Array<*>).map {
            (it as FloatArray).toList()
        }
        gnnFeaturesList.add(output)
        return output
    }

    private fun runRnn(): List<Float> {
        if (path.size == 0) {
            return listOf()
        }
        if (rnnSession === null) {
            rnnSession = env.createSession(rnnModelPath, OrtSession.SessionOptions())
        }
        val lastActionData = path.last()
        val lastChosenAction = lastActionData.chosenStateId
        val gnnFeaturesCount = gnnFeaturesList.firstOrNull()?.first()?.size ?: 0
        val gnnFeatures = gnnFeaturesList.getOrNull(lastActionData.graphId)?.getOrNull(
            lastActionData.blockIds[lastChosenAction]
        )
            ?: List(gnnFeaturesCount) { 0.0f }
        val lastActionFeatures = super.getAllFeatures(
            lastActionData.queue[lastChosenAction], lastActionData,
            lastActionData.blockIds[lastChosenAction]
        ) + gnnFeatures
        val lastActionShape = listOf(1, lastActionFeatures.size.toLong())
        val lastStateShape = mlConfig.rnnStateShape
        val actionFeaturesDataBuffer = FloatBuffer.allocate(lastActionShape.prod().toInt())
        val stateFeaturesDataBuffer = FloatBuffer.allocate(lastStateShape.prod().toInt())
        lastActionFeatures.forEach {
            actionFeaturesDataBuffer.put(it)
        }
        actionFeaturesDataBuffer.rewind()
        lastStateFeatures.forEach {
            stateFeaturesDataBuffer.put(it)
        }
        stateFeaturesDataBuffer.rewind()
        val actionFeaturesData = OnnxTensor.createTensor(env, actionFeaturesDataBuffer, lastActionShape.toLongArray())
        val stateFeaturesData = OnnxTensor.createTensor(env, stateFeaturesDataBuffer, lastStateShape.toLongArray())
        val result = rnnSession!!.run(mapOf(Pair("input", actionFeaturesData), Pair("state_in", stateFeaturesData)))
        lastStateFeatures = (result.get("state_out").get().value as Array<*>).flatMap {
            ((it as Array<*>)[0] as FloatArray).toList()
        }
        rnnFeatures = (result.get("rnn_features").get().value as Array<*>).flatMap {
            (it as FloatArray).toList()
        }
        return rnnFeatures
    }

    private fun runActor(allFeaturesListFull: List<List<Float>>): Int {
        val firstIndex = if (mlConfig.maxAttentionLength == -1) 0 else
            maxOf(0, lru.size - mlConfig.maxAttentionLength)
        val allFeaturesList = allFeaturesListFull.subList(firstIndex, lru.size)
        val totalSize = allFeaturesList.size * allFeaturesList.first().size
        val totalKnownSize = mlConfig.inputShape.prod()
        val shape = mlConfig.inputShape.map { if (it != -1L) it else -totalSize / totalKnownSize }.toLongArray()
        val dataBuffer = FloatBuffer.allocate(totalSize)
        allFeaturesList.forEach { stateFeatures ->
            stateFeatures.forEach { feature ->
                dataBuffer.put(feature)
            }
        }
        dataBuffer.rewind()
        val data = OnnxTensor.createTensor(env, dataBuffer, shape)
        val result = actorSession!!.run(mapOf(Pair("input", data)))
        val output = (result.get("output").get().value as Array<*>).flatMap { (it as FloatArray).toList() }
        outputValues = List(firstIndex) { -1.0f } + output
        return firstIndex + when (mlConfig.postprocessing) {
            Postprocessing.Argmax -> {
                output.indices.maxBy { output[it] }
            }
            Postprocessing.Softmax -> {
                val exponents = output.map { exp(it) }
                val exponentsSum = exponents.sum()
                val softmaxProbabilities = exponents.map { it / exponentsSum }
                probabilities.add(softmaxProbabilities)
                chooseRandomId(softmaxProbabilities)
            }
            else -> {
                probabilities.add(output)
                chooseRandomId(output)
            }
        }
    }

    private fun peekWithOnnxRuntime(
        stateFeatureQueue: List<StateFeatures>?,
        globalStateFeatures: GlobalStateFeatures?
    ): State {
        if (stateFeatureQueue == null || globalStateFeatures == null) {
            throw IllegalArgumentException("No features")
        }
        if (lru.size == 1) {
            if (mlConfig.postprocessing != Postprocessing.Argmax) {
                probabilities.add(listOf(1.0f))
            }
            return lru[0]
        }
        val graphFeatures = gnnFeaturesList.lastOrNull() ?: listOf()
        val blockFeaturesCount = graphFeatures.firstOrNull()?.size ?: 0
        val allFeaturesListFull = stateFeatureQueue.zip(lru).map { (stateFeatures, state) ->
            stateFeaturesToFloatList(stateFeatures) + globalStateFeaturesToFloatList(globalStateFeatures) +
                    (blockGraph.getBlock(state.currentStatement!!)?.id?.let { graphFeatures.getOrNull(it) }
                        ?: List(blockFeaturesCount) { 0.0f }) +
                    rnnFeatures
        }
        return lru[runActor(allFeaturesListFull)]
    }

    override fun getExtraFeatures(): List<Float> {
        return rnnFeatures
    }

    override fun getAllFeatures(stateFeatures: StateFeatures, actionData: ActionData, blockId: Int): List<Float> {
        val gnnFeaturesCount = gnnFeaturesList.firstOrNull()?.first()?.size ?: 0
        val gnnFeatures = gnnFeaturesList.getOrNull(actionData.graphId)?.getOrNull(blockId)
            ?: List(gnnFeaturesCount) { 0.0f }
        return super.getAllFeatures(stateFeatures, actionData, blockId) + gnnFeatures + actionData.extraFeatures
    }

    override fun peek(): State {
        val (stateFeatureQueue, globalStateFeatures) = beforePeek()
        if (mlConfig.useRnn) {
            runRnn()
        }
        if (mlConfig.useGnn) {
            runGnn()
        }
        val state = if (actorSession !== null) {
            peekWithOnnxRuntime(stateFeatureQueue, globalStateFeatures)
        } else {
            defaultPathSelector.peek()
        }
        afterPeek(state, stateFeatureQueue, globalStateFeatures)
        return state
    }
}
