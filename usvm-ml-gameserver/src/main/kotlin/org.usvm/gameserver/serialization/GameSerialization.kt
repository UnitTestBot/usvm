package org.usvm.gameserver.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.usvm.gameserver.GameStep
import org.usvm.gameserver.GameStepHidden
import org.usvm.gameserver.StateId

object GameStepSerializer : KSerializer<GameStep> {
    override val descriptor: SerialDescriptor = GameStepHidden.serializer().descriptor

    override fun serialize(encoder: Encoder, value: GameStep) {
        encoder.encodeSerializableValue(GameStepHidden.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): GameStep {
        val json = decoder as JsonDecoder
        val jsonElement = json.decodeJsonElement() as JsonObject
        val excessKeys = jsonElement.keys.filter { it !in listOf("StateId") }
        if (excessKeys.isEmpty() || excessKeys.count() == 1 && excessKeys[0] == "PredictedStateUsefulness") {
            val stateId = Json.decodeFromString<StateId>(jsonElement["StateId"]!!.jsonPrimitive.content)
            return GameStep(stateId)
        }
        throw SerializationException("Unknown key")
    }
}