package org.usvm.gameserver

import kotlinx.serialization.*
import org.usvm.gameserver.serialization.*


@Serializable
enum class InputMessageType {
    @SerialName("start")
    Start,

    @SerialName("step")
    Step,

    @SerialName("stop")
    Stop
}

@Serializable
data class RawInputMessage(
    @SerialName("MessageType") val messageType: InputMessageType,
    @SerialName("MessageBody") val messageBody: InputMessageBody
)

@Serializable(with = InputMessageBodyPolymorphicSerializer::class)
sealed class InputMessageBody

@Serializable(with = StartAsGameMapSerializer::class)
data class Start(@SerialName("GameMap") val gameMap: GameMap) : InputMessageBody()

@Serializable
data object ServerStop : InputMessageBody()


@Serializable(with = StepAsGameStepSerializer::class)
data class Step(@SerialName("GameStep") val gameStep: GameStep) : InputMessageBody()


fun RawInputMessage.toInputBody(): InputMessageBody {
    return when (this.messageType) {
        InputMessageType.Start -> this.messageBody
        InputMessageType.Step -> this.messageBody
        InputMessageType.Stop -> ServerStop
    }
}


@Serializable
enum class OutputMessageType {
    GameOver,
    MoveReward,
    IncorrectPredictedStateId,
    ReadyForNextStep,
    Stop
}


@Serializable(with = OutputMessageBodySerializer::class)
sealed class OutputMessageBody


@Serializable
data class GameOver(
    @SerialName("Percent") val percent: UByte,
    @SerialName("Test") val test: UInt,
    @SerialName("Error") val error: UInt
) : OutputMessageBody()

@Serializable(with = MoveRewardAsRewardSerializer::class)
data class MoveReward(@SerialName("Reward") val reward: Reward) : OutputMessageBody()

@Serializable(with = IncorrectPredictedStateIdAsStateIdSerializer::class)
data class IncorrectPredictedStateId(@SerialName("StateId") val stateId: StateId) : OutputMessageBody()


@Serializable(with = ReadyForNextStepAsGameStateSerializer::class)
data class ReadyForNextStep(@SerialName("GameState") val gameState: GameState) : OutputMessageBody()


@Serializable
data class ServerError(@SerialName("Message") val message: String) : OutputMessageBody()

fun OutputMessageBody.toRawOutputMessage(): RawOutputMessage {
    val type = when (this) {
        is GameOver -> OutputMessageType.GameOver
        is MoveReward -> OutputMessageType.MoveReward
        is IncorrectPredictedStateId -> OutputMessageType.IncorrectPredictedStateId
        is ReadyForNextStep -> OutputMessageType.ReadyForNextStep
        is ServerError -> OutputMessageType.Stop
    }
    return RawOutputMessage(type, this)
}

@Serializable
data class RawOutputMessage(
    @SerialName("MessageType") val messageType: OutputMessageType,
    @SerialName("MessageBody") val messageBody: OutputMessageBody
)

