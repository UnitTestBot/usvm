package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDomain
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FastCheckProjectionClientTest {
    private val client = FastCheckProjectionClient()

    @Test
    fun `Kotlin domains produce deterministic tagged fast-check samples`() {
        val request = FastCheckProjectionRequest(
            seed = 42,
            numSamples = 20,
            domains = listOf(IntegerDomain(-10, 10), ArrayDomain(BooleanDomain, 0, 3)),
        )

        val first = client.sample(request)
        val second = client.sample(request)

        assertEquals(first, second)
        assertEquals(20, first.samples.size)
        first.samples.forEach { sample -> assertConforms(sample, request.domains) }
    }

    @Test
    fun `protocol version mismatch is a typed backend error`() {
        val error = assertFailsWith<FastCheckProjectionException> {
            client.sample(validRequest.copy(protocolVersion = 999))
        }

        assertEquals("protocol.version.unsupported", error.code)
        assertEquals("protocolVersion", error.path)
    }

    @Test
    fun `invalid request is rejected before starting Node`() {
        val missingAdapterClient = FastCheckProjectionClient(
            nodeExecutable = "definitely-not-a-node-executable",
            adapterEntryPoint = Path.of("missing-adapter.mjs"),
        )

        val error = assertFailsWith<FastCheckProjectionException> {
            missingAdapterClient.sample(validRequest.copy(numSamples = 0))
        }

        assertEquals("protocol.request.invalid", error.code)
    }

    @Test
    fun `process startup and exit failures are typed transport errors`() {
        val startup = assertFailsWith<FastCheckProjectionException> {
            FastCheckProjectionClient(
                nodeExecutable = "definitely-not-a-node-executable",
            ).sample(validRequest)
        }
        assertEquals("backend.process.start.failed", startup.code)

        val exit = assertFailsWith<FastCheckProjectionException> {
            FastCheckProjectionClient(
                adapterEntryPoint = Path.of("missing-adapter.mjs"),
            ).sample(validRequest)
        }
        assertEquals("backend.process.failed", exit.code)
    }

    @Test
    fun `invalid protocol output is a typed transport error`() {
        withTemporaryAdapter("process.stdout.write('not-json\\n')") { temporaryClient ->
            val malformed = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(validRequest)
            }
            assertEquals("backend.response.invalid", malformed.code)
        }

        withTemporaryAdapter("") { temporaryClient ->
            val empty = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(validRequest)
            }
            assertEquals("backend.response.empty", empty.code)
        }
    }

    @Test
    fun `response protocol mismatch is rejected`() {
        withTemporaryAdapter(
            """
            process.stdout.write(JSON.stringify({
              protocolVersion: 2,
              status: 'ok',
              samples: []
            }))
            """.trimIndent(),
        ) { temporaryClient ->
            val error = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(validRequest)
            }
            assertEquals("backend.response.mismatch", error.code)
        }
    }

    @Test
    fun `large adapter stderr does not block a successful response`() {
        withTemporaryAdapter(
            """
            const timeout = setTimeout(() => process.exit(2), 1000)
            process.stderr.write('x'.repeat(1024 * 1024), () => {
              clearTimeout(timeout)
              process.stdout.write(JSON.stringify({
                protocolVersion: 1,
                status: 'ok',
                samples: [[{ kind: 'boolean', value: true }]]
              }))
            })
            """.trimIndent(),
        ) { temporaryClient ->
            val response = temporaryClient.sample(validRequest)

            assertEquals(
                listOf(listOf(JsConcreteValue.Boolean(true))),
                response.samples,
            )
        }
    }

    private fun assertConforms(values: List<JsConcreteValue>, domains: List<PropertyDomain>) {
        assertEquals(domains.size, values.size)
        values.zip(domains).forEach { (value, domain) ->
            when (domain) {
                is IntegerDomain -> {
                    val number = (value as JsConcreteValue.Number).toDouble()
                    val isInteger = number % 1.0 == 0.0
                    val isWithinBounds = number >= domain.min && number <= domain.max

                    assertTrue(isInteger && isWithinBounds)
                }

                is ArrayDomain -> {
                    (value as JsConcreteValue.Array).elements.forEach { element ->
                        assertConforms(listOf(element), listOf(domain.element))
                    }
                }

                BooleanDomain -> {
                    assertTrue(value is JsConcreteValue.Boolean)
                }

                else -> {
                    error("Unexpected test domain: $domain")
                }
            }
        }
    }

    private fun withTemporaryAdapter(source: String, block: (FastCheckProjectionClient) -> Unit) {
        val script = createTempFile(prefix = "fast-check-adapter-", suffix = ".mjs")
        try {
            script.writeText(source)
            block(FastCheckProjectionClient(adapterEntryPoint = script))
        } finally {
            script.deleteIfExists()
        }
    }

    private companion object {
        val validRequest = FastCheckProjectionRequest(
            seed = 42,
            numSamples = 1,
            domains = listOf(BooleanDomain),
        )
    }
}
