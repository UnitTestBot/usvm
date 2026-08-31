package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDomain
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FastCheckProjectionClientTest {
    @Test
    fun `transport limits reject a shutdown grace period outside the Node timer range`() {
        assertFailsWith<IllegalArgumentException> {
            transportLimits(shutdownGraceMillis = 2_147_483_648L)
        }
    }

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
    fun `requests above the projection sample cap are rejected before starting Node`() {
        val missingAdapterClient = FastCheckProjectionClient(
            nodeExecutable = "definitely-not-a-node-executable",
            adapterEntryPoint = Path.of("missing-adapter.mjs"),
        )

        val error = assertFailsWith<FastCheckProjectionException> {
            missingAdapterClient.sample(validRequest.copy(numSamples = 10_001))
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
    fun `successful samples outside their domains are rejected`() {
        withTemporaryAdapter(
            """
            process.stdout.write(JSON.stringify({
              status: 'ok',
              samples: [[{ kind: 'boolean', value: true }]]
            }))
            """.trimIndent(),
        ) { temporaryClient ->
            val error = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(
                    validRequest.copy(domains = listOf(IntegerDomain(min = 0, max = 1))),
                )
            }

            assertEquals("backend.response.invalid", error.code)
            assertEquals("samples[0][0]", error.path)
        }
    }

    @Test
    fun `requests beyond the transport byte limit are rejected before starting Node`() {
        withTemporaryAdapter(
            source = "",
            transportLimits = transportLimits(maxRequestBytes = 100),
        ) { temporaryClient ->
            val error = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(
                    validRequest.copy(
                        domains = listOf(ConstantDomain(JsConcreteValue.String("x".repeat(101)))),
                    ),
                )
            }

            assertEquals("backend.request.too-large", error.code)
        }
    }

    @Test
    fun `stdout beyond the transport byte limit is rejected`() {
        withTemporaryAdapter(
            source = "process.stdout.write('x'.repeat(1025))",
            transportLimits = transportLimits(maxStdoutBytes = 1_024),
        ) { temporaryClient ->
            val error = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(validRequest)
            }

            assertEquals("backend.response.too-large", error.code)
        }
    }

    @Test
    fun `stderr beyond the transport byte limit is rejected`() {
        withTemporaryAdapter(
            source = """
                process.stderr.write('x'.repeat(1025))
                process.stdout.write(JSON.stringify({
                  status: 'ok',
                  samples: [[{ kind: 'boolean', value: true }]]
                }))
            """.trimIndent(),
            transportLimits = transportLimits(maxStderrBytes = 1_024),
        ) { temporaryClient ->
            val error = assertFailsWith<FastCheckProjectionException> {
                temporaryClient.sample(validRequest)
            }

            assertEquals("backend.response.too-large", error.code)
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    fun `continuing stdout beyond the transport byte limit fails promptly`() {
        val pidFile = createTempFile(prefix = "fast-check-stdout-pid-", suffix = ".txt")
        pidFile.deleteIfExists()

        try {
            withTemporaryAdapter(
                source = """
                    import { writeFileSync } from 'node:fs'
                    writeFileSync(${pidFile.toJavaScriptStringLiteral()}, String(process.pid))
                    process.stdout.on('error', () => undefined)
                    process.on('SIGTERM', () => undefined)
                    setInterval(() => process.stdout.write('x'.repeat(1025)), 1)
                """.trimIndent(),
                transportLimits = transportLimits(
                    maxStdoutBytes = 1_024,
                    wallClockTimeoutMillis = 250,
                    shutdownGraceMillis = 500,
                ),
            ) { temporaryClient ->
                val startedAt = System.nanoTime()
                val error = assertFailsWith<FastCheckProjectionException> {
                    temporaryClient.sample(validRequest)
                }
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

                assertEquals("backend.response.too-large", error.code)
                assertTrue(elapsedMillis < 2_000, "Stdout limit took $elapsedMillis ms")
                assertTrue(adapterIsTerminated(pidFile), "Stdout adapter is still running")
            }
        } finally {
            terminateAdapter(pidFile)
            pidFile.deleteIfExists()
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    fun `continuing stderr beyond the transport byte limit fails promptly`() {
        val pidFile = createTempFile(prefix = "fast-check-stderr-pid-", suffix = ".txt")
        pidFile.deleteIfExists()

        try {
            withTemporaryAdapter(
                source = """
                    import { writeFileSync } from 'node:fs'
                    writeFileSync(${pidFile.toJavaScriptStringLiteral()}, String(process.pid))
                    setInterval(() => process.stderr.write('x'.repeat(1025)), 1)
                """.trimIndent(),
                transportLimits = transportLimits(maxStderrBytes = 1_024),
            ) { temporaryClient ->
                val startedAt = System.nanoTime()
                val error = assertFailsWith<FastCheckProjectionException> {
                    temporaryClient.sample(validRequest)
                }
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

                assertEquals("backend.response.too-large", error.code)
                assertTrue(elapsedMillis < 2_000, "Stderr limit took $elapsedMillis ms")
                assertTrue(adapterIsTerminated(pidFile), "Stderr adapter is still running")
            }
        } finally {
            terminateAdapter(pidFile)
            pidFile.deleteIfExists()
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    fun `immediate parent exit still terminates a descendant retaining a pipe`() {
        val childPidFile = createTempFile(prefix = "fast-check-descendant-pid-", suffix = ".txt")
        childPidFile.deleteIfExists()

        try {
            withTemporaryAdapter(
                source = """
                    import { spawn } from 'node:child_process'
                    import { writeFileSync } from 'node:fs'
                    const child = spawn(process.execPath, [
                      '-e',
                      "process.on('SIGTERM', () => undefined); setInterval(() => undefined, 1000)"
                    ], { stdio: 'inherit' })
                    writeFileSync(${childPidFile.toJavaScriptStringLiteral()}, String(child.pid))
                    process.exit(0)
                """.trimIndent(),
                transportLimits = transportLimits(
                    wallClockTimeoutMillis = 250,
                    shutdownGraceMillis = 500,
                ),
            ) { temporaryClient ->
                val startedAt = System.nanoTime()
                val error = assertFailsWith<FastCheckProjectionException> {
                    temporaryClient.sample(validRequest)
                }
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

                assertEquals("backend.response.empty", error.code)
                assertTrue(elapsedMillis < 600, "Descendant cleanup took $elapsedMillis ms")
                assertTrue(adapterIsTerminated(childPidFile), "Descendant is still running")
            }
        } finally {
            assertTrue(terminateAdapter(childPidFile), "Test cleanup did not terminate descendant")
            childPidFile.deleteIfExists()
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    fun `wall clock timeout returns promptly and terminates the adapter`() {
        val pidFile = createTempFile(prefix = "fast-check-adapter-pid-", suffix = ".txt")
        pidFile.deleteIfExists()

        try {
            withTemporaryAdapter(
                source = """
                    import { writeFileSync } from 'node:fs'
                    writeFileSync(${pidFile.toJavaScriptStringLiteral()}, String(process.pid))
                    setInterval(() => undefined, 1_000)
                """.trimIndent(),
                transportLimits = transportLimits(wallClockTimeoutMillis = 250),
            ) { temporaryClient ->
                val startedAt = System.nanoTime()
                val error = assertFailsWith<FastCheckProjectionException> {
                    temporaryClient.sample(validRequest)
                }
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

                assertEquals("backend.process.timeout", error.code)
                assertTrue(elapsedMillis < 600, "Projection timeout took $elapsedMillis ms")
                assertTrue(adapterIsTerminated(pidFile), "Adapter is still running")
            }
        } finally {
            terminateAdapter(pidFile)
            pidFile.deleteIfExists()
        }
    }

    @Test
    fun `large adapter stderr does not block a successful response`() {
        withTemporaryAdapter(
            """
            const timeout = setTimeout(() => process.exit(2), 1000)
            process.stderr.write('x'.repeat(32 * 1024), () => {
              clearTimeout(timeout)
              process.stdout.write(JSON.stringify({
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

    private fun withTemporaryAdapter(
        source: String,
        transportLimits: FastCheckProjectionTransportLimits? = null,
        block: (FastCheckProjectionClient) -> Unit,
    ) {
        val script = createTempFile(prefix = "fast-check-adapter-", suffix = ".mjs")

        try {
            script.writeText(source)
            val client = transportLimits?.let { limits ->
                FastCheckProjectionClient(
                    adapterEntryPoint = script,
                    transportLimits = limits,
                )
            } ?: FastCheckProjectionClient(adapterEntryPoint = script)

            block(client)
        } finally {
            script.deleteIfExists()
        }
    }

    private fun transportLimits(
        maxRequestBytes: Int = 1_024,
        maxStdoutBytes: Int = 1_024,
        maxStderrBytes: Int = 1_024,
        wallClockTimeoutMillis: Long = 1_000,
        shutdownGraceMillis: Long = 25,
    ) = FastCheckProjectionTransportLimits(
        maxRequestBytes = maxRequestBytes,
        maxStdoutBytes = maxStdoutBytes,
        maxStderrBytes = maxStderrBytes,
        wallClockTimeoutMillis = wallClockTimeoutMillis,
        shutdownGraceMillis = shutdownGraceMillis,
    )

    private fun Path.toJavaScriptStringLiteral(): String = "'${toString().replace("\\", "\\\\").replace("'", "\\'")}'"

    private fun terminateAdapter(pidFile: Path): Boolean {
        val pid = pidFile.takeIf(Files::exists)?.readText()?.trim()?.toLongOrNull() ?: return true
        val process = ProcessHandle.of(pid).orElse(null) ?: return true

        process.destroyForcibly()

        try {
            process.onExit().get(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()

            return true
        }

        return !process.isAlive
    }

    private fun adapterIsTerminated(pidFile: Path): Boolean {
        val pid = pidFile.readText().trim().toLong()
        val process = ProcessHandle.of(pid).orElse(null)
        if (process == null || !process.isAlive) return true

        try {
            process.onExit().get(1, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            return false
        } catch (_: ExecutionException) {
            return !process.isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()

            return !process.isAlive
        }

        return !process.isAlive
    }

    private companion object {
        val validRequest = FastCheckProjectionRequest(
            seed = 42,
            numSamples = 1,
            domains = listOf(BooleanDomain),
        )
    }
}
