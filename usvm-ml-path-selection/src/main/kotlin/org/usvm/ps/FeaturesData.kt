package org.usvm.ps

import kotlinx.serialization.Serializable

@Serializable
internal data class StateFeatures(
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
internal data class GlobalStateFeatures(
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
internal data class ActionData(
    val queue: List<StateFeatures>,
    val globalStateFeatures: GlobalStateFeatures,
    val chosenStateId: Int,
    var reward: Float,
    val graphId: Int = 0,
    val blockIds: List<Int>,
    val extraFeatures: List<Float>,
)

internal fun stateFeaturesToFloatList(stateFeatures: StateFeatures): List<Float> {
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

internal fun globalStateFeaturesToFloatList(globalStateFeatures: GlobalStateFeatures): List<Float> {
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

internal fun blockFeaturesToList(blockFeatures: BlockFeatures): List<Float> {
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
