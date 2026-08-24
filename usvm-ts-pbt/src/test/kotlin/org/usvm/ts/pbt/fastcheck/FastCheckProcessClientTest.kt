package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FastCheckProcessClientTest {
    @Test
    fun `process startup failure is typed`() {
        val startup = assertFailsWith<PbtBackendException> {
            FastCheckProcessClient(
                nodeExecutable = "definitely-not-a-node-executable",
                adapterEntryPoint = Path.of("missing-adapter.mjs"),
            ).check(validRequest)
        }

        assertEquals(BackendErrorKind.PROCESS_FAILURE, startup.kind)
        assertEquals("backend.process.start.failed", startup.code)
    }

    @Test
    fun `non-zero exit retains stderr`() {
        withTemporaryAdapter(source = "process.stderr.write('adapter failed'); process.exit(3)") { client ->
            val exit = assertFailsWith<PbtBackendException> { client.check(validRequest) }

            assertEquals(BackendErrorKind.PROCESS_FAILURE, exit.kind)
            assertEquals("backend.process.failed", exit.code)
            assertTrue(exit.message.orEmpty().contains("adapter failed"))
        }
    }

    @Test
    fun `empty malformed and unknown responses are protocol errors`() {
        val cases = listOf(
            InvalidResponseCase(script = "", expectedCode = "backend.response.empty"),
            InvalidResponseCase(
                script = "process.stdout.write('not-json')",
                expectedCode = "backend.response.invalid",
            ),
            InvalidResponseCase(
                script = """
                    process.stdout.write(JSON.stringify({
                      status: 'unknown'
                    }))
                """.trimIndent(),
                expectedCode = "backend.response.invalid",
            ),
        )

        cases.forEach { case ->
            withTemporaryAdapter(source = case.script) { client ->
                val error = assertFailsWith<PbtBackendException> { client.check(validRequest) }

                assertEquals(BackendErrorKind.PROTOCOL_ERROR, error.kind)
                assertEquals(case.expectedCode, error.code)
            }
        }
    }

    @Test
    fun `Node diagnostic category does not depend on code naming`() {
        withTemporaryAdapter(
            source = """
            process.stdout.write(JSON.stringify({
              status: 'error',
              diagnostics: [{
                kind: 'entry-point',
                code: 'adapter.module.failure',
                message: 'module missing',
                path: 'manifest.predicate.module'
              }]
            }))
            """.trimIndent(),
        ) { client ->
            val error = assertFailsWith<PbtBackendException> { client.check(validRequest) }

            assertEquals(BackendErrorKind.ENTRY_POINT, error.kind)
            assertEquals("adapter.module.failure", error.code)
            assertEquals("manifest.predicate.module", error.path)
        }
    }

    @Test
    fun `hard timeout terminates a stuck Node process`() {
        withTemporaryAdapter(
            source = "setInterval(() => undefined, 1000)",
            transportGraceMillis = 25,
        ) { client ->
            val startedAt = System.nanoTime()
            val error = assertFailsWith<PbtBackendException> {
                client.check(validRequest.copy(timeoutMillis = 25))
            }
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertEquals(BackendErrorKind.TIMEOUT, error.kind)
            assertEquals("backend.process.timeout", error.code)
            assertTrue(elapsedMillis < 2_000, "Process timeout took $elapsedMillis ms")
        }
    }

    private fun withTemporaryAdapter(
        source: String,
        transportGraceMillis: Long = 2_000,
        block: (FastCheckProcessClient) -> Unit,
    ) {
        val script = createTempFile(prefix = "fast-check-execution-", suffix = ".mjs")

        try {
            script.writeText(source)
            block(
                FastCheckProcessClient(
                    adapterEntryPoint = script,
                    transportGraceMillis = transportGraceMillis,
                ),
            )
        } finally {
            script.deleteIfExists()
        }
    }

    private companion object {
        data class InvalidResponseCase(
            val script: String,
            val expectedCode: String,
        )

        val property = PropertyDefinition(
            id = PropertyId("example.property"),
            inputs = listOf(PropertyInput(name = "value", domain = BooleanDomain)),
            predicate = TypeScriptEntryPoint(
                module = "property.ts",
                exportName = "predicate",
            ),
        )

        val validRequest = FastCheckExecutionRequest(
            manifest = property.toManifest(),
            sourceRoots = listOf(testResourcesRoot().toString()),
            seed = 42,
            numRuns = 10,
            timeoutMillis = 1_000,
        )
    }
}
