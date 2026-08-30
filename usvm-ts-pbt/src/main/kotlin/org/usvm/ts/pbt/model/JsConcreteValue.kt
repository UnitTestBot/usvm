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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Tags the finite and non-finite cases of an ECMAScript binary64 value. */
@Serializable
enum class JsNumberKind {
    /** Finite value represented by its raw IEEE-754 bits. */
    @SerialName("finite")
    FINITE,

    /** JavaScript NaN value. */
    @SerialName("nan")
    NAN,

    /** Positive infinity. */
    @SerialName("positive-infinity")
    POSITIVE_INFINITY,

    /** Negative infinity. */
    @SerialName("negative-infinity")
    NEGATIVE_INFINITY,
}

/** Lossless tagged representation of a JavaScript number, including NaN, infinities, and negative zero. */
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
                bits = value
                    .toRawBits()
                    .toULong()
                    .toString(JS_NUMBER_HEX_RADIX)
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

/** Lossless transport value for JavaScript primitives and recursively nested arrays. */
@Serializable(with = JsConcreteValueSerializer::class)
sealed interface JsConcreteValue {
    /** JavaScript `undefined`. */
    data object Undefined : JsConcreteValue

    /** JavaScript `null`. */
    data object Null : JsConcreteValue

    /** Concrete JavaScript boolean value. */
    data class Boolean(val value: kotlin.Boolean) : JsConcreteValue

    /** Concrete JavaScript UTF-16 string value. */
    data class String(val value: kotlin.String) : JsConcreteValue

    /** Concrete JavaScript binary64 number with lossless special-value encoding. */
    data class Number(val number: JsNumber) : JsConcreteValue {
        fun toDouble(): Double = number.toDouble()
    }

    /** Ordered recursively tagged elements of one concrete JavaScript array. */
    data class Array(val elements: List<JsConcreteValue>) : JsConcreteValue

    companion object {
        /** Creates a lossless tagged value from any ECMAScript binary64 number. */
        fun number(value: Double): Number = Number(JsNumber.fromDouble(value))
    }
}

/** JSON serializer for the tagged [JsConcreteValue] wire representation. */
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
                        val elements = value.elements.map { element ->
                            jsonEncoder.json.encodeToJsonElement(JsConcreteValueSerializer, element)
                        }

                        val jsonElements = JsonArray(elements)

                        put("kind", "array")
                        put("elements", jsonElements)
                    }
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): JsConcreteValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("JsConcreteValue supports JSON deserialization only")

        val value = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("JsConcreteValue must be a JSON object")

        return when (val kind = value.requiredString("kind")) {
            "undefined" -> {
                value.requireExactKeys("kind")
                JsConcreteValue.Undefined
            }

            "null" -> {
                value.requireExactKeys("kind")
                JsConcreteValue.Null
            }

            "boolean" -> {
                value.requireExactKeys("kind", "value")
                deserializeBoolean(value)
            }

            "string" -> {
                value.requireExactKeys("kind", "value")
                JsConcreteValue.String(value.requiredString("value"))
            }

            "number" -> {
                deserializeNumber(value)
            }

            "array" -> {
                value.requireExactKeys("kind", "elements")
                deserializeArray(jsonDecoder, value)
            }

            else -> {
                throw SerializationException("Unknown JavaScript value kind: $kind")
            }
        }
    }
}

private fun deserializeBoolean(value: JsonObject): JsConcreteValue.Boolean {
    val primitive = value["value"] as? JsonPrimitive
        ?: throw SerializationException("Boolean JsConcreteValue requires a boolean value")
    val booleanValue = primitive.takeUnless(JsonPrimitive::isString)?.booleanOrNull
        ?: throw SerializationException("Boolean JsConcreteValue requires a boolean value")

    return JsConcreteValue.Boolean(booleanValue)
}

private fun deserializeNumber(value: JsonObject): JsConcreteValue.Number {
    val numberKindName = value.requiredString("value")
    val numberKind = when (numberKindName) {
        "finite" -> JsNumberKind.FINITE
        "nan" -> JsNumberKind.NAN
        "positive-infinity" -> JsNumberKind.POSITIVE_INFINITY
        "negative-infinity" -> JsNumberKind.NEGATIVE_INFINITY
        else -> throw SerializationException("Unknown JavaScript number kind: $numberKindName")
    }

    val bits = when (numberKind) {
        JsNumberKind.FINITE -> {
            value.requireExactKeys("kind", "value", "bits")
            value.requiredFiniteBits()
        }

        JsNumberKind.NAN,
        JsNumberKind.POSITIVE_INFINITY,
        JsNumberKind.NEGATIVE_INFINITY,
        -> {
            value.requireExactKeys("kind", "value")
            null
        }
    }
    val number = JsNumber(value = numberKind, bits = bits)

    return JsConcreteValue.Number(number)
}

private fun deserializeArray(jsonDecoder: JsonDecoder, value: JsonObject): JsConcreteValue.Array {
    val jsonElements = value["elements"] as? JsonArray
        ?: throw SerializationException("Array JsConcreteValue requires elements")

    val elements = jsonElements.map { element ->
        jsonDecoder.json.decodeFromJsonElement(JsConcreteValueSerializer, element)
    }

    return JsConcreteValue.Array(elements)
}

private val JsNumberKind.serialName: String
    get() = when (this) {
        JsNumberKind.FINITE -> "finite"
        JsNumberKind.NAN -> "nan"
        JsNumberKind.POSITIVE_INFINITY -> "positive-infinity"
        JsNumberKind.NEGATIVE_INFINITY -> "negative-infinity"
    }

private fun JsonObject.requireExactKeys(vararg expectedKeys: String) {
    if (keys != expectedKeys.toSet()) {
        throw SerializationException("JsConcreteValue has unexpected fields")
    }
}

private fun JsonObject.requiredString(name: String): String {
    val value = get(name) as? JsonPrimitive
        ?: throw SerializationException("JsConcreteValue requires a string $name field")
    if (!value.isString) {
        throw SerializationException("JsConcreteValue requires a string $name field")
    }

    return value.content
}

private fun JsonObject.requiredFiniteBits(): String {
    val bits = requiredString("bits")
    if (!bits.matches(FINITE_NUMBER_BITS_REGEX)) {
        throw SerializationException("Finite JsConcreteValue requires sixteen lowercase hexadecimal bits")
    }

    val number = Double.fromBits(bits.toULong(JS_NUMBER_HEX_RADIX).toLong())
    if (!number.isFinite()) {
        throw SerializationException("Finite JsConcreteValue requires finite IEEE-754 bits")
    }

    return bits
}

private const val JS_NUMBER_HEX_DIGITS = 16
private const val JS_NUMBER_HEX_RADIX = 16
private val FINITE_NUMBER_BITS_REGEX = Regex("[0-9a-f]{16}")
