package org.usvm.gameserver.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.usvm.errors.ExcessKeysError
import org.usvm.gameserver.*


object InputMessageBodyPolymorphicSerializer :
    JsonContentPolymorphicSerializer<InputMessageBody>(InputMessageBody::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out InputMessageBody> {
        if (element == JsonObject(mapOf())) {
            return ServerStop.serializer()
        }
        val strArgs = element.jsonPrimitive.content
        return when {
            strArgs.contains("StateId") -> Step.serializer()
            strArgs.contains("StepsToPlay") ->
                Start.serializer()

            else -> ServerStop.serializer()
        }
    }
}


object StartAsGameMapSerializer : KSerializer<Start> {
    override val descriptor: SerialDescriptor = GameMap.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Start) {
        val string = Json.encodeToString(value.gameMap)
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): Start {
        val jsonAsString = decoder.decodeString()
        val gameMap = Json.decodeFromString<GameMap>(jsonAsString)
        return Start(gameMap)
    }
}

object StepAsGameStepSerializer : KSerializer<Step> {
    private val ACCEPTABLE_KEYS = arrayOf("StateId")
    private val ACCEPTABLE_EXCESS_KEYS = arrayOf("PredictedStateUsefulness")

    override val descriptor: SerialDescriptor = Step.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Step) {
        val string = Json.encodeToString(value.gameStep)
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): Step {
        val jsonAsString = decoder.decodeString()
        val jsonObject = Json.parseToJsonElement(jsonAsString).jsonObject
        if (jsonObject.keys.all {
                ACCEPTABLE_KEYS.contains(
                    it
                ) || ACCEPTABLE_EXCESS_KEYS.contains(it)
            }) {
            val argsMap = mutableMapOf<String, JsonElement>()
            for ((key, value) in jsonObject) {
                if (!ACCEPTABLE_EXCESS_KEYS.contains(key)) {
                    argsMap[key] = value
                }
            }
            val args = JsonObject(argsMap)
            val reward = Json.decodeFromJsonElement<GameStep>(args)
            return Step(reward)
        } else {
            throw ExcessKeysError(jsonObject.filterKeys { ACCEPTABLE_EXCESS_KEYS.contains(it) }.keys.toTypedArray())
        }
    }
}


object OutputMessageBodySerializer : JsonContentPolymorphicSerializer<OutputMessageBody>(OutputMessageBody::class) {
    @OptIn(InternalSerializationApi::class)
    override fun selectDeserializer(element: JsonElement): KSerializer<out OutputMessageBody> {
        val jsonObject = element.jsonObject
        val outputMessageBodyClass = when {
            "Percent" in jsonObject -> GameOver::class
            "ForMove" in jsonObject -> MoveReward::class
            "StateId" in jsonObject -> IncorrectPredictedStateId::class
            "GraphVertices" in jsonObject -> ReadyForNextStep::class
            "Message" in jsonObject -> ServerError::class
            else -> OutputMessageBody::class
        }
        return outputMessageBodyClass.serializer()
    }
}

object MoveRewardAsRewardSerializer : KSerializer<MoveReward> {
    override val descriptor: SerialDescriptor = Reward.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MoveReward) {
        encoder.encodeSerializableValue(Reward.serializer(), value.reward)
    }

    override fun deserialize(decoder: Decoder): MoveReward {
        val reward = decoder.decodeSerializableValue(Reward.serializer())
        return MoveReward(reward)
    }
}

object IncorrectPredictedStateIdAsStateIdSerializer : KSerializer<IncorrectPredictedStateId> {
    override val descriptor: SerialDescriptor = StateId.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IncorrectPredictedStateId) {
        encoder.encodeSerializableValue(StateId.serializer(), value.stateId)
    }

    override fun deserialize(decoder: Decoder): IncorrectPredictedStateId {
        val string = decoder.decodeString()
        return IncorrectPredictedStateId(Json.decodeFromString<StateId>(string))
    }
}

object ReadyForNextStepAsGameStateSerializer : KSerializer<ReadyForNextStep> {
    override val descriptor: SerialDescriptor = GameState.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ReadyForNextStep) {
        encoder.encodeSerializableValue(GameState.serializer(), value.gameState)
    }

    override fun deserialize(decoder: Decoder): ReadyForNextStep {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return ReadyForNextStep(decoder.json.decodeFromJsonElement(GameState.serializer(), element))
    }
}