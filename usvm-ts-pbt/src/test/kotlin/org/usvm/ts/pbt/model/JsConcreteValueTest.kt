package org.usvm.ts.pbt.model

import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import kotlin.test.assertEquals

class JsConcreteValueTest {
    @Test
    fun `number factory creates a lossless concrete value`() {
        val value = JsConcreteValue.number(-0.0)

        assertEquals((-0.0).toRawBits(), value.toDouble().toRawBits())
    }

    @Test
    fun `special JavaScript numbers keep their semantics through JSON`() {
        val values = listOf(
            JsConcreteValue.Number(JsNumber.fromDouble(-0.0)),
            JsConcreteValue.Number(JsNumber.fromDouble(Double.NaN)),
            JsConcreteValue.Number(JsNumber.fromDouble(Double.POSITIVE_INFINITY)),
            JsConcreteValue.Number(JsNumber.fromDouble(Double.NEGATIVE_INFINITY)),
        )

        values.forEach { value ->
            val encoded = PropertyManifestJson.json.encodeToString<JsConcreteValue>(value)
            val decoded = PropertyManifestJson.json.decodeFromString<JsConcreteValue>(encoded)

            assertEquals(value, decoded)
        }

        val negativeZero = values.first() as JsConcreteValue.Number

        assertEquals((-0.0).toRawBits(), negativeZero.toDouble().toRawBits())
    }

    @Test
    fun `finite JavaScript numbers use sixteen lowercase hexadecimal digits`() {
        assertEquals("3ff8000000000000", JsNumber.finite(1.5).bits)
        assertEquals("8000000000000000", JsNumber.finite(-0.0).bits)
    }

    @Test
    fun `recursive arrays keep tagged values through JSON`() {
        val value = JsConcreteValue.Array(
            listOf(
                JsConcreteValue.Undefined,
                JsConcreteValue.Array(listOf(JsConcreteValue.Number(JsNumber.finite(-0.0)))),
            ),
        )

        val encoded = PropertyManifestJson.json.encodeToString<JsConcreteValue>(value)

        assertEquals(value, PropertyManifestJson.json.decodeFromString<JsConcreteValue>(encoded))
    }
}
