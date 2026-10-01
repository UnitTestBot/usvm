package org.usvm.ts.pbt.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Utf16JsonTest {
    @Test
    fun `all UTF-16 code units survive JSON UTF-8 transport`() {
        val source = (Char.MIN_VALUE..Char.MAX_VALUE).joinToString(separator = "")
        val value: JsConcreteValue = JsConcreteValue.Array(listOf(JsConcreteValue.String(source)))

        val encoded = Json.encodeToUtf8SafeString(value)
        val transported = encoded.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)

        assertFalse(encoded.any { it.isSurrogate() })
        assertEquals(value, Json.decodeFromString<JsConcreteValue>(transported))
    }

    @Test
    fun `plain JSON remains unchanged and escaping is idempotent`() {
        val original = Json.encodeToString("quotes: \" slash: \\ literal: \\ud800 Cyrillic: привет")
        val surrogate = Json.encodeToUtf8SafeString("\ud800\udc00\udfff")

        assertEquals(original, escapeJsonSurrogates(original))
        assertEquals(surrogate, escapeJsonSurrogates(surrogate))
    }

    @Test
    fun `different isolated surrogates retain distinct UTF-8 hashes`() {
        val digests = listOf("\ud800", "\ud801", "?").map { value ->
            val encoded = Json.encodeToUtf8SafeString<JsConcreteValue>(JsConcreteValue.String(value))
            MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray(Charsets.UTF_8)).toList()
        }

        assertEquals(3, digests.distinct().size)
        assertNotEquals(digests[0], digests[1])
    }

    @Test
    fun `ordinary JsonElement roundtrip retains string contents`() {
        val value: JsConcreteValue = JsConcreteValue.Array(listOf(JsConcreteValue.String("\ud800")))

        val tree = Json.encodeToJsonElement(JsConcreteValueSerializer, value)
        val decoded = Json.decodeFromJsonElement(JsConcreteValueSerializer, tree)

        assertEquals(value, decoded)
    }

    @Test
    fun `Node receives exact UTF-16 units through the unchanged tagged schema`() {
        val source = "\ud800?\udfff\ud83d\ude00\"\\\n"
        val encoded = Json.encodeToUtf8SafeString<JsConcreteValue>(JsConcreteValue.String(source))
        val script = """
            const input = JSON.parse(require('fs').readFileSync(0, 'utf8'));
            if (input.kind !== 'string') process.exit(2);
            const units = Array.from({length: input.value.length}, (_, i) => input.value.charCodeAt(i));
            process.stdout.write(JSON.stringify(units));
        """.trimIndent()
        val process = ProcessBuilder("node", "-e", script).redirectErrorStream(true).start()

        try {
            process.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()

            assertEquals(0, process.exitValue(), output)
            assertEquals(source.map { it.code }, Json.decodeFromString<List<Int>>(output))
        } finally {
            process.destroyForcibly()
        }
    }
}
