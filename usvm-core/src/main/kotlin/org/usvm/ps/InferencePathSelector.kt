package org.usvm.ps

import org.usvm.UState
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics

import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor

import io.kinference.model.Model
import io.kinference.ndarray.arrays.FloatNDArray
import kotlinx.coroutines.runBlocking
import java.io.File

internal class InferencePathSelector<State : UState<*, *, Method, Statement>, Statement, Method> (
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>
) : BfsWithLoggingPathSelector<State, Statement, Method>(
    pathsTreeStatistics,
    coverageStatistics,
    distanceStatistics,
    applicationGraph
) {
    private val modelPath = "./model/model.onnx"
    private val model = runBlocking { Model.load(File(modelPath).readBytes(), KIEngine) }

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

    private fun peekWithModel(stateFeatureQueue: List<StateFeatures>?,
                              averageStateFeatures: AverageStateFeatures?) : State {
        if (stateFeatureQueue == null || averageStateFeatures == null) {
            throw IllegalArgumentException("No features")
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
            (model.predict(listOf(data))["output"] as KITensor).data as FloatNDArray
        }.array.toArray()
        val stateId = output.indices.maxBy { output[it] }
        return queue[stateId]
    }

    override fun peek(): State {
        val stateFeatureQueue = getStateFeatureQueue()
        val averageStateFeatures = getAverageStateFeatures(stateFeatureQueue)
        val state = peekWithModel(stateFeatureQueue, averageStateFeatures)
        path.add(getActionData(stateFeatureQueue, averageStateFeatures, state))
        savePath()
        updateCoverage(state)
        return state
    }
}
