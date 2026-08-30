package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
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
    fun `coverage workspace is removed when process startup fails`() {
        val runtime = createTempDirectory(prefix = "fast-check-runtime-")
        val adapter = runtime.resolve("dist/src/execution-cli.js")
        val c8 = runtime.resolve("node_modules/c8/bin/c8.js")
        val fakeNode = runtime.resolve("fake-node")
        adapter.parent.createDirectories()
        c8.parent.createDirectories()
        adapter.createFile()
        c8.createFile()
        fakeNode.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
                printf 'v22.14.0\n'
                rm "${'$'}0"
                exit 0
            fi
            exit 1
            """.trimIndent(),
        )
        check(fakeNode.toFile().setExecutable(true))
        val workspacesBefore = coverageWorkspaces()

        try {
            val error = assertFailsWith<PbtBackendException> {
                FastCheckProcessClient(
                    nodeExecutable = fakeNode.toString(),
                    adapterEntryPoint = adapter,
                ).check(
                    validRequest.copy(coverageRequest = PropertyCoverageRequest()),
                )
            }

            assertEquals(BackendErrorKind.PROCESS_FAILURE, error.kind)
            assertEquals(workspacesBefore, coverageWorkspaces())
        } finally {
            runtime.toFile().deleteRecursively()
            coverageWorkspaces()
                .minus(workspacesBefore)
                .forEach { workspace -> workspace.toFile().deleteRecursively() }
        }
    }

    @Test
    fun `coverage rejects an unsupported Node runtime before starting c8`() {
        val runtime = createTempDirectory(prefix = "fast-check-runtime-")
        val adapter = runtime.resolve("dist/src/execution-cli.js")
        val c8 = runtime.resolve("node_modules/c8/bin/c8.js")
        val collectorStarted = runtime.resolve("collector-started")
        val fakeNode = runtime.resolve("fake-node")
        adapter.parent.createDirectories()
        c8.parent.createDirectories()
        adapter.createFile()
        c8.createFile()
        fakeNode.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
                printf 'v16.20.2\n'
                exit 0
            fi
            touch "$collectorStarted"
            exit 1
            """.trimIndent(),
        )
        check(fakeNode.toFile().setExecutable(true))

        try {
            val error = assertFailsWith<PbtBackendException> {
                FastCheckProcessClient(
                    nodeExecutable = fakeNode.toString(),
                    adapterEntryPoint = adapter,
                ).check(
                    validRequest.copy(coverageRequest = PropertyCoverageRequest()),
                )
            }

            assertEquals(BackendErrorKind.COVERAGE, error.kind)
            assertEquals("coverage.runtime.unsupported", error.code)
            assertTrue(Files.notExists(collectorStarted))
        } finally {
            runtime.toFile().deleteRecursively()
        }
    }

    @Test
    fun `coverage collector preserves the caller working directory`() {
        val runtime = createTempDirectory(prefix = "fast-check-runtime-")
        val adapter = runtime.resolve("dist/src/execution-cli.js")
        val c8 = runtime.resolve("node_modules/c8/bin/c8.js")
        val collectorDirectory = runtime.resolve("collector-directory")
        val fakeNode = runtime.resolve("fake-node")
        adapter.parent.createDirectories()
        c8.parent.createDirectories()
        adapter.createFile()
        c8.createFile()
        fakeNode.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
                printf 'v22.14.0\n'
                exit 0
            fi
            pwd > "$collectorDirectory"
            exit 1
            """.trimIndent(),
        )
        check(fakeNode.toFile().setExecutable(true))
        val callerDirectory = Path.of("").toAbsolutePath().normalize()

        try {
            val error = assertFailsWith<PbtBackendException> {
                FastCheckProcessClient(
                    nodeExecutable = fakeNode.toString(),
                    adapterEntryPoint = adapter,
                ).check(
                    validRequest.copy(coverageRequest = PropertyCoverageRequest()),
                )
            }
            val startedDirectory = Path.of(collectorDirectory.readText().trim())

            assertEquals(BackendErrorKind.PROCESS_FAILURE, error.kind)
            assertEquals(callerDirectory, startedDirectory)
        } finally {
            runtime.toFile().deleteRecursively()
        }
    }

    @Test
    fun `coverage collector uses an explicit empty configuration`() {
        val runtime = createTempDirectory(prefix = "fast-check-runtime-")
        val adapter = runtime.resolve("dist/src/execution-cli.js")
        val c8 = runtime.resolve("node_modules/c8/bin/c8.js")
        val collectorArguments = runtime.resolve("collector-arguments")
        val collectorConfiguration = runtime.resolve("collector-configuration")
        val fakeNode = runtime.resolve("fake-node")
        adapter.parent.createDirectories()
        c8.parent.createDirectories()
        adapter.createFile()
        c8.createFile()
        fakeNode.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
                printf 'v22.14.0\n'
                exit 0
            fi
            printf '%s\n' "${'$'}@" > "$collectorArguments"
            for argument in "${'$'}@"; do
                case "${'$'}argument" in
                    --config=*)
                        config_path="${'$'}{argument#--config=}"
                        cat "${'$'}config_path" > "$collectorConfiguration"
                        ;;
                esac
            done
            exit 1
            """.trimIndent(),
        )
        check(fakeNode.toFile().setExecutable(true))

        try {
            val error = assertFailsWith<PbtBackendException> {
                FastCheckProcessClient(
                    nodeExecutable = fakeNode.toString(),
                    adapterEntryPoint = adapter,
                ).check(
                    validRequest.copy(coverageRequest = PropertyCoverageRequest()),
                )
            }
            val configArguments = collectorArguments.readText()
                .lineSequence()
                .filter { argument -> argument.startsWith("--config=") }
                .toList()

            assertEquals(BackendErrorKind.PROCESS_FAILURE, error.kind)
            assertEquals(1, configArguments.size)
            assertEquals("{}", collectorConfiguration.readText())
        } finally {
            runtime.toFile().deleteRecursively()
        }
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

    @Test
    fun `hard deadline includes inherited descendant pipe drain`() {
        val childPidFile = createTempFile(prefix = "fast-check-inherited-pipe-pid-", suffix = ".txt")
        childPidFile.deleteIfExists()

        try {
            withTemporaryAdapter(
                source = """
                    import { spawn } from 'node:child_process'
                    import { writeFileSync } from 'node:fs'

                    const child = spawn(
                      process.execPath,
                      ['-e', 'setTimeout(() => undefined, 30000)'],
                      { stdio: ['ignore', 'inherit', 'inherit'] }
                    )
                    writeFileSync(${childPidFile.toJavaScriptStringLiteral()}, String(child.pid))
                    child.unref()

                    process.stdout.write(JSON.stringify({
                      status: 'ok',
                      result: {
                        propertyId: 'example.property',
                        status: 'success',
                        seed: 42,
                        replayPath: null,
                        counterexample: null,
                        numRuns: 1,
                        numSkips: 0,
                        numShrinks: 0,
                        failure: null,
                        executionTimeMillis: 1
                      }
                    }))
                """.trimIndent(),
                transportGraceMillis = 100,
            ) { client ->
                val error = assertFailsWith<PbtBackendException> {
                    client.check(validRequest.copy(timeoutMillis = 100))
                }

                assertEquals(BackendErrorKind.TIMEOUT, error.kind)
                assertEquals("backend.process.timeout", error.code)
                assertTrue(processIsAlive(childPidFile), "Inherited-pipe descendant exited before the hard deadline")
            }
        } finally {
            terminateProcess(childPidFile)
            childPidFile.deleteIfExists()
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

    private fun coverageWorkspaces(): Set<Path> {
        val temporaryRoot = Path.of(System.getProperty("java.io.tmpdir"))
        return Files.newDirectoryStream(temporaryRoot, "usvm-ts-pbt-coverage-*").use { entries ->
            entries.toHashSet()
        }
    }

    private fun Path.toJavaScriptStringLiteral(): String = "'${toString().replace("\\", "\\\\").replace("'", "\\'")}'"

    private fun processIsAlive(pidFile: Path): Boolean {
        val pid = pidFile.takeIf(Files::exists)?.readText()?.trim()?.toLongOrNull() ?: return false
        val process = ProcessHandle.of(pid).orElse(null) ?: return false

        return process.isAlive
    }

    private fun terminateProcess(pidFile: Path) {
        val pid = pidFile.takeIf(Files::exists)?.readText()?.trim()?.toLongOrNull() ?: return
        val process = ProcessHandle.of(pid).orElse(null) ?: return

        process.destroyForcibly()
        process.onExit().get(1, TimeUnit.SECONDS)
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
