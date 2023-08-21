package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.usvm.*
import org.usvm.statistics.*
import org.usvm.util.RandomizedPriorityCollection
import java.io.File
import java.lang.UnsupportedOperationException
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

internal open class InferencePathSelector<State : UState<*, *, Method, Statement, *, State>, Statement, Method> (
    pathsTreeRoot: PathsTrieNode<State, Statement>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) : BfsWithLoggingPathSelector<State, Statement, Method>(
    pathsTreeRoot,
    coverageStatistics,
    distanceStatistics,
    applicationGraph
) {
    private var outputValues = listOf<Float>()
    private var chosenStateId = 0
    private val random = Random(java.time.LocalDateTime.now().nano)
    private val gnnFeaturesList = mutableListOf<List<List<Float>>>()

    private fun <State : UState<*, *, *, *, *, State>> compareById(): Comparator<State> = compareBy { it.id }
    private val forkDepthRandomPathSelector = WeightedPathSelector<State, Double>(
        { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        { 1.0 / max(it.pathLocation.depth.toDouble(), 1.0) }
    )

    companion object {
        private val actorModelPath = Path(MainConfig.gameEnvPath, "actor_model.onnx").toString()
        private val gnnModelPath = Path(MainConfig.gameEnvPath, "gnn_model.onnx").toString()
        private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
        private var actorSession: OrtSession? = null
        private var gnnSession: OrtSession? = null
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
            .intersect(state.reversedPath.asSequence().toSet()).size.toFloat()
    }

    override fun getNodeName(node: PathsTrieNode<State, Statement>, id: Int): String {
        val statement = try {
            node.statement
        } catch (e: UnsupportedOperationException) {
            "No Statement"
        }
        var name = "\"$id: $statement"
        node.states.forEach { state ->
            name += ", ${DecimalFormat("0.00E0").format(outputValues.getOrElse(queue.indexOf(state)) { -1.0f })}"
        }
        name += "\""
        return name
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
        val featuresDataBuffer = FloatBuffer.allocate(featuresShape.reduce { acc, i -> acc * i })
        graphFeatures.forEach { blockFeatures ->
            blockFeatures.forEach { feature ->
                featuresDataBuffer.put(feature)
            }
        }
        featuresDataBuffer.rewind()
        val edgesDataBuffer = LongBuffer.allocate(edgesShape.reduce { acc, i -> acc * i})
        graphEdges.forEach { nodes ->
            nodes.forEach { node ->
                edgesDataBuffer.put(node.toLong())
            }
        }
        edgesDataBuffer.rewind()
        val featuresData = OnnxTensor.createTensor(env, featuresDataBuffer,
            featuresShape.map { it.toLong() }.toLongArray())
        val edgesData = OnnxTensor.createTensor(env, edgesDataBuffer,
            edgesShape.map { it.toLong() }.toLongArray())
        val result = gnnSession!!.run(mapOf(Pair("x", featuresData), Pair("edge_index", edgesData)))
        val output = (result.get("output").get().value as Array<*>).map {
            (it as FloatArray).toList()
        }
        gnnFeaturesList.add(output)
        return output
    }

    private fun runActor(allFeaturesListFull: List<List<Float>>) {
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
        val result = actorSession!!.run(mapOf(Pair("input", data)))
        val output = (result.get("output").get().value as Array<*>).flatMap { (it as FloatArray).toList() }
        chosenStateId = firstIndex + when (MainConfig.postprocessing) {
            Postprocessing.Argmax -> {
                output.indices.maxBy { output[it] }
            }
            Postprocessing.Softmax -> {
                val exponents = output.map { exp(it) }
                val exponentsSum = exponents.sum()
                val softmaxProbabilities = exponents.map { it /exponentsSum }
                probabilities.add(softmaxProbabilities)
                chooseRandomId(softmaxProbabilities)
            }
            else -> {
                probabilities.add(output)
                chooseRandomId(output)
            }
        }
        outputValues = List(firstIndex) { -1.0f } + output
    }

    private fun peekWithOnnxRuntime(stateFeatureQueue: List<StateFeatures>?,
                                    globalStateFeatures: GlobalStateFeatures?,
                                    graphFeatures: List<List<Float>>): State {
        if (stateFeatureQueue == null || globalStateFeatures == null) {
            throw IllegalArgumentException("No features")
        }
        if (actorSession === null) {
            actorSession = env.createSession(actorModelPath, OrtSession.SessionOptions())
        }
        val blockFeaturesCount = graphFeatures.firstOrNull()?.size ?: 0
        val allFeaturesListFull = stateFeatureQueue.zip(queue).map { (stateFeatures, state) ->
            stateFeaturesToFloatList(stateFeatures) + globalStateFeaturesToFloatList(globalStateFeatures) +
            (blockGraph.getBlock(state.currentStatement!!)?.id?.let { graphFeatures.getOrNull(it) }
                ?: List(blockFeaturesCount) { 0.0f })
        }
        runActor(allFeaturesListFull)
        return queue[chosenStateId]
    }

    override fun getAllFeatures(stateFeatures: StateFeatures, actionData: ActionData, blockId: Int): List<Float> {
        val gnnFeaturesCount = gnnFeaturesList.firstOrNull()?.first()?.size ?: 0
        val gnnFeatures = gnnFeaturesList.getOrNull(actionData.graphId)?.getOrNull(blockId)
            ?: List(gnnFeaturesCount) { 0.0f }
        return super.getAllFeatures(stateFeatures, actionData, blockId) + gnnFeatures
    }

    override fun peek(): State {
        val (stateFeatureQueue, globalStateFeatures) = beforePeek()
        val graphFeatures = if (MainConfig.useGnn) runGnn() else listOf()
        val state = if (File(actorModelPath).isFile) {
            peekWithOnnxRuntime(stateFeatureQueue, globalStateFeatures, graphFeatures)
        } else if (MainConfig.defaultAlgorithm == Algorithm.BFS) {
            queue.first()
        } else {
            forkDepthRandomPathSelector.peek()
        }
        afterPeek(state, stateFeatureQueue, globalStateFeatures)
        return state
    }

    override fun remove(state: State) {
        super.remove(state)
        if (MainConfig.defaultAlgorithm == Algorithm.ForkDepthRandom) {
            forkDepthRandomPathSelector.remove(state)
        }
    }

    override fun add(states: Collection<State>) {
        super.add(states)
        if (MainConfig.defaultAlgorithm == Algorithm.ForkDepthRandom) {
            forkDepthRandomPathSelector.add(states)
        }
    }

    override fun update(state: State) {
        super.update(state)
        if (MainConfig.defaultAlgorithm == Algorithm.ForkDepthRandom) {
            forkDepthRandomPathSelector.update(state)
        }
    }
}
