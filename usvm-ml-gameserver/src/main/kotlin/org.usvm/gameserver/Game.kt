package org.usvm.gameserver

import kotlinx.serialization.*
import org.usvm.gameserver.serialization.GameStepSerializer

@Serializable
enum class Searcher { BFS, DFS }

typealias StateId = @Serializable UInt

@Serializable
data class GameStepHidden(@SerialName("StateId") val stateId: StateId)

typealias GameStep = @Serializable(with = GameStepSerializer::class) GameStepHidden


@Serializable
data class StateHistoryElem(
    @SerialName("GraphVertexId") val graphVertexId: UInt,
    @SerialName("NumOfVisits") val numOfVisits: UInt,
    @SerialName("StepWhenVisitedLastTime") val stepWhenVisitedLastTime: UInt
)

@Serializable
data class State(
    @SerialName("Id") val id: UInt,
    @SerialName("Position") val position: UInt,
    @SerialName("PathConditionSize") val pathConditionSize: UInt,
    @SerialName("VisitedAgainVertices") val visitedAgainVertices: UInt,
    @SerialName("VisitedNotCoveredVerticesInZone") val visitedNotCoveredVerticesInZone: UInt,
    @SerialName("VisitedNotCoveredVerticesOutOfZone") val visitedNotCoveredVerticesOutOfZone: UInt,
    @SerialName("StepWhenMovedLastTime") val stepWhenMovedLastTime: UInt,
    @SerialName("InstructionsVisitedInCurrentBlock") val instructionsVisitedInCurrentBlock: UInt,
    @SerialName("History") val history: List<StateHistoryElem>,
    @SerialName("Children") val children: List<UInt>
)

@Serializable
data class GameMapVertex(
    @SerialName("Id") val id: UInt,
    @SerialName("InCoverageZone") val inCoverageZone: Boolean,
    @SerialName("BasicBlockSize") val basicBlockSize: UInt,
    @SerialName("CoveredByTest") val coveredByTest: Boolean,
    @SerialName("VisitedByState") val visitedByState: Boolean,
    @SerialName("TouchedByState") val touchedByState: Boolean,
    @SerialName("ContainsCall") val containsCall: Boolean,
    @SerialName("ContainsThrow") val containsThrow: Boolean,
    @SerialName("States") val states: List<StateId>
)

@Serializable
data class GameEdgeLabel(@SerialName("Token") val token: Int)

@Serializable
data class GameMapEdge(
    @SerialName("VertexFrom") val vertexFrom: UInt,
    @SerialName("VertexTo") val vertexTo: UInt,
    @SerialName("Label") val label: GameEdgeLabel
)

@Serializable
data class GameState(
    @SerialName("GraphVertices") val graphVertices: List<GameMapVertex>,
    @SerialName("States") val states: List<State>,
    @SerialName("Map") val map: List<GameMapEdge>
)

@Serializable
data class MoveRewardData(
    @SerialName("ForCoverage") val forCoverage: UInt,
    @SerialName("ForVisitedInstructions") val forVisitedInstructions: UInt
)

@Serializable
data class Reward(
    @SerialName("ForMove") val forMove: MoveRewardData,
    @SerialName("MaxPossibleReward") val maxPossibleReward: UInt
) {
    constructor(forCoverage: UInt, forVisitedInstructions: UInt, maxPossibleReward: UInt) :
            this(MoveRewardData(forCoverage, forVisitedInstructions), maxPossibleReward)
}

@Serializable
data class GameMap(
    @SerialName("StepsToPlay") val stepsToPlay: UInt,
    @SerialName("StepsToStart") val stepsToStart: UInt,
    @SerialName("DefaultSearcher") val defaultSearcher: Searcher,
    @SerialName("AssemblyFullName") val assemblyFullName: String,
    @SerialName("NameOfObjectToCover") val nameOfObjectToCover: String,
    @SerialName("MapName") val mapName: String
) {
    constructor(
        stepsToPlay: UInt,
        stepsToStart: UInt,
        defaultSearcher: Searcher,
        assemblyFullName: String,
        nameOfObjectToCover: String
    ) : this(
        stepsToPlay,
        stepsToStart,
        defaultSearcher,
        assemblyFullName,
        nameOfObjectToCover,
        "${nameOfObjectToCover}_${defaultSearcher}_${stepsToStart}"
    )
}