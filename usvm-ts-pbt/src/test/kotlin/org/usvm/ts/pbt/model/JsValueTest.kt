package org.usvm.ts.pbt.model

import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import kotlin.test.assertEquals

class JsValueTest {
    @Test
    fun `special JavaScript numbers keep their semantics through JSON`() {
        val values = listOf(
            JsValue.Number(JsNumber.fromDouble(-0.0)),
            JsValue.Number(JsNumber.fromDouble(Double.NaN)),
            JsValue.Number(JsNumber.fromDouble(Double.POSITIVE_INFINITY)),
            JsValue.Number(JsNumber.fromDouble(Double.NEGATIVE_INFINITY)),
        )

        values.forEach { value ->
            val encoded = PropertyManifestJson.json.encodeToString<JsValue>(value)
            val decoded = PropertyManifestJson.json.decodeFromString<JsValue>(encoded)
            assertEquals(value, decoded)
        }

        val negativeZero = values.first() as JsValue.Number
        assertEquals((-0.0).toRawBits(), negativeZero.toDouble().toRawBits())
    }

    @Test
    fun `finite JavaScript numbers use sixteen lowercase hexadecimal digits`() {
        assertEquals("3ff8000000000000", JsNumber.finite(1.5).bits)
        assertEquals("8000000000000000", JsNumber.finite(-0.0).bits)
    }
}
