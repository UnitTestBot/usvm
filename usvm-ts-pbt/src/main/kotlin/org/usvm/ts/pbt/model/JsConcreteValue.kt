package org.usvm.ts.pbt.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
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
                .toULong(JS_NUMBER_HEX_RADIX)
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
                bits = value.toRawBits().toULong().toString(JS_NUMBER_HEX_RADIX)
                    .padStart(JS_NUMBER_HEX_DIGITS, '0'),
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

@Serializable(with = JsConcreteValueSerializer::class)
sealed interface JsConcreteValue {
    data object Undefined : JsConcreteValue

    data object Null : JsConcreteValue

    data class Boolean(val value: kotlin.Boolean) : JsConcreteValue

    data class String(val value: kotlin.String) : JsConcreteValue

    data class Number(val number: JsNumber) : JsConcreteValue {
        fun toDouble(): Double = number.toDouble()
    }

    data class Array(val elements: List<JsConcreteValue>) : JsConcreteValue
}

object JsConcreteValueSerializer : KSerializer<JsConcreteValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JsConcreteValue")

    override fun serialize(encoder: Encoder, value: JsConcreteValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("JsConcreteValue supports JSON serialization only")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                when (value) {
                    JsConcreteValue.Undefined -> {
                        put("kind", "undefined")
                    }

                    JsConcreteValue.Null -> {
                        put("kind", "null")
                    }
                    is JsConcreteValue.Boolean -> {
                        put("kind", "boolean")
                        put("value", value.value)
                    }

                    is JsConcreteValue.String -> {
                        put("kind", "string")
                        put("value", value.value)
                    }

                    is JsConcreteValue.Number -> {
                        put("kind", "number")
                        put("value", value.number.value.serialName)
                        value.number.bits?.let { put("bits", it) }
                    }

                    is JsConcreteValue.Array -> {
                        put("kind", "array")
                        put(
                            "elements",
                            JsonArray(
                                value.elements.map { element ->
                                    jsonEncoder.json.encodeToJsonElement(JsConcreteValueSerializer, element)
                                },
                            ),
                        )
                    }
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): JsConcreteValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("JsConcreteValue supports JSON deserialization only")
        val value = jsonDecoder.decodeJsonElement().jsonObject
        return when (val kind = value.requiredString("kind")) {
            "undefined" -> JsConcreteValue.Undefined
            "null" -> JsConcreteValue.Null
            "boolean" -> JsConcreteValue.Boolean(
                value["value"]?.jsonPrimitive?.booleanOrNull
                    ?: throw SerializationException("Boolean JsConcreteValue requires a boolean value"),
            )

            "string" -> JsConcreteValue.String(value.requiredString("value"))
            "number" -> JsConcreteValue.Number(
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

            "array" -> JsConcreteValue.Array(
                value["elements"]?.jsonArray?.map { element ->
                    jsonDecoder.json.decodeFromJsonElement(JsConcreteValueSerializer, element)
                } ?: throw SerializationException("Array JsConcreteValue requires elements"),
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
        ?: throw SerializationException("JsConcreteValue requires a $name field")

private const val JS_NUMBER_HEX_DIGITS = 16
private const val JS_NUMBER_HEX_RADIX = 16
