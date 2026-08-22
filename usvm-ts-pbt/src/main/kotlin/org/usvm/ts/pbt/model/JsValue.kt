package org.usvm.ts.pbt.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
enum class JsNumberKind {
    @SerialName("finite")
    FINITE,

    @SerialName("nan")
    NAN,

    @SerialName("positive-infinity")
    POSITIVE_INFINITY,

    @SerialName("negative-infinity")
    NEGATIVE_INFINITY,
}

@Serializable
data class JsNumber(
    val value: JsNumberKind,
    val bits: String? = null,
) {
    fun toDouble(): Double = when (value) {
        JsNumberKind.FINITE -> Double.fromBits(
            requireNotNull(bits) { "A finite JavaScript number requires IEEE-754 bits" }
                .toULong(16)
                .toLong(),
        )

        JsNumberKind.NAN -> Double.NaN
        JsNumberKind.POSITIVE_INFINITY -> Double.POSITIVE_INFINITY
        JsNumberKind.NEGATIVE_INFINITY -> Double.NEGATIVE_INFINITY
    }

    companion object {
        fun finite(value: Double): JsNumber {
            require(value.isFinite()) { "Use a tagged representation for non-finite JavaScript numbers" }
            return JsNumber(
                value = JsNumberKind.FINITE,
                bits = value.toRawBits().toULong().toString(16).padStart(JS_NUMBER_HEX_DIGITS, '0'),
            )
        }

        fun fromDouble(value: Double): JsNumber = when {
            value.isNaN() -> nan()
            value == Double.POSITIVE_INFINITY -> positiveInfinity()
            value == Double.NEGATIVE_INFINITY -> negativeInfinity()
            else -> finite(value)
        }

        fun nan(): JsNumber = JsNumber(JsNumberKind.NAN)

        fun positiveInfinity(): JsNumber = JsNumber(JsNumberKind.POSITIVE_INFINITY)

        fun negativeInfinity(): JsNumber = JsNumber(JsNumberKind.NEGATIVE_INFINITY)
    }
}

@Serializable(with = JsValueSerializer::class)
sealed interface JsValue {
    data object Undefined : JsValue

    data object Null : JsValue

    data class Boolean(val value: kotlin.Boolean) : JsValue

    data class String(val value: kotlin.String) : JsValue

    data class Number(val number: JsNumber) : JsValue {
        fun toDouble(): Double = number.toDouble()
    }
}

object JsValueSerializer : KSerializer<JsValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JsValue")

    override fun serialize(encoder: Encoder, value: JsValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("JsValue supports JSON serialization only")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                when (value) {
                    JsValue.Undefined -> put("kind", "undefined")
                    JsValue.Null -> put("kind", "null")
                    is JsValue.Boolean -> {
                        put("kind", "boolean")
                        put("value", value.value)
                    }

                    is JsValue.String -> {
                        put("kind", "string")
                        put("value", value.value)
                    }

                    is JsValue.Number -> {
                        put("kind", "number")
                        put("value", value.number.value.serialName)
                        value.number.bits?.let { put("bits", it) }
                    }
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): JsValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("JsValue supports JSON deserialization only")
        val value = jsonDecoder.decodeJsonElement().jsonObject
        return when (val kind = value.requiredString("kind")) {
            "undefined" -> JsValue.Undefined
            "null" -> JsValue.Null
            "boolean" -> JsValue.Boolean(
                value["value"]?.jsonPrimitive?.booleanOrNull
                    ?: throw SerializationException("Boolean JsValue requires a boolean value"),
            )

            "string" -> JsValue.String(value.requiredString("value"))
            "number" -> JsValue.Number(
                JsNumber(
                    value = when (val numberKind = value.requiredString("value")) {
                        "finite" -> JsNumberKind.FINITE
                        "nan" -> JsNumberKind.NAN
                        "positive-infinity" -> JsNumberKind.POSITIVE_INFINITY
                        "negative-infinity" -> JsNumberKind.NEGATIVE_INFINITY
                        else -> throw SerializationException("Unknown JavaScript number kind: $numberKind")
                    },
                    bits = value["bits"]?.jsonPrimitive?.content,
                ),
            )

            else -> throw SerializationException("Unknown JavaScript value kind: $kind")
        }
    }
}

private val JsNumberKind.serialName: kotlin.String
    get() = when (this) {
        JsNumberKind.FINITE -> "finite"
        JsNumberKind.NAN -> "nan"
        JsNumberKind.POSITIVE_INFINITY -> "positive-infinity"
        JsNumberKind.NEGATIVE_INFINITY -> "negative-infinity"
    }

private fun kotlinx.serialization.json.JsonObject.requiredString(name: kotlin.String): kotlin.String =
    get(name)?.jsonPrimitive?.content
        ?: throw SerializationException("JsValue requires a $name field")

private const val JS_NUMBER_HEX_DIGITS = 16
