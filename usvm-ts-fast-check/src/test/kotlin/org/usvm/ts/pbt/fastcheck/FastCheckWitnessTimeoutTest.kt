package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FastCheckWitnessTimeoutTest {
    @Test
    fun `expired replay budget returns timeout without starting a process`() {
        val client = FastCheckProcessClient(
            nodeExecutable = "definitely-not-a-node-executable",
            adapterEntryPoint = Path.of("unused"),
        )

        val result = client.witness(request = request(), remainingMillis = { 0L })

        assertIs<FastCheckOperationResult.TimedOut>(result)
    }

    @Test
    fun `expired runtime probe budget returns timeout without starting a process`() {
        val result = FastCheckCoverageSession.prepareWithinBudget(
            nodeExecutable = "definitely-not-a-node-executable",
            adapterEntryPoint = Path.of("unused"),
            request = request().copy(coverageRequest = PropertyCoverageRequest()),
            remainingMillis = { 0L },
        )

        assertIs<FastCheckOperationResult.TimedOut>(result)
    }

    @Test
    fun `budget expiry after runtime probe releases its coverage workspace`() {
        val before = coverageWorkspaces()
        var budgetReads = 0
        val client = FastCheckProcessClient(adapterEntryPoint = FastCheckRuntime.executionEntryPoint())

        val result = client.witness(
            request = request().copy(coverageRequest = PropertyCoverageRequest()),
            remainingMillis = {
                budgetReads += 1
                if (budgetReads <= 2) 5_000L else 0L
            },
        )

        assertIs<FastCheckOperationResult.TimedOut>(result)
        assertEquals(3, budgetReads)
        assertEquals(before, coverageWorkspaces())
    }

    @Test
    fun `expired branch conversion budget returns timeout before reading its report`() {
        var remaining = 5_000L
        val preparation = FastCheckCoverageSession.prepareWithinBudget(
            nodeExecutable = "node",
            adapterEntryPoint = FastCheckRuntime.executionEntryPoint(),
            request = request().copy(coverageRequest = PropertyCoverageRequest()),
            remainingMillis = { remaining },
        )
        val session = assertIs<FastCheckOperationResult.Completed<FastCheckCoverageSession>>(preparation).value
        remaining = 0L

        session.use {
            val result = it.readArtifact()

            assertIs<FastCheckOperationResult.TimedOut>(result)
        }
    }

    @Test
    fun `running witness timeout returns a result and terminates its process`() {
        val directory = createTempDirectory(prefix = "witness-timeout-")
        val adapter = directory.resolve("adapter.mjs")
        val pidFile = directory.resolve("pid")
        adapter.writeText(
            """
            import { writeFileSync } from 'node:fs';
            writeFileSync(${jsString(pidFile.toString())}, String(process.pid));
            setInterval(() => {}, 1000);
            """.trimIndent(),
        )
        val client = FastCheckProcessClient(adapterEntryPoint = adapter)

        try {
            val result = client.witness(request = request(), remainingMillis = { 500L })

            assertIs<FastCheckOperationResult.TimedOut>(result)
            assertTrue(Files.isRegularFile(pidFile), "The supervised adapter did not start")
            val pid = pidFile.readText().toLong()
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun request(): FastCheckExecutionRequest {
        val module = "properties/witness/ExternalWitness.ts"
        val property = PropertyDefinition(
            id = PropertyId(value = "witness.timeout"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 0, max = 100))),
            predicate = TypeScriptEntryPoint(module = module, exportName = "threshold"),
        )

        return FastCheckExecutionRequest(
            manifest = property.toManifest(),
            sourceRoots = listOf(testResourcesRoot().toString()),
            numRuns = 1,
            timeoutMillis = 5_000,
            witness = FastCheckWitnessOperation(
                mode = "replay",
                inputs = listOf(JsConcreteValue.number(80.0)),
                target = WitnessTarget(id = "threshold"),
                sources = listOf(PropertyObservationSource(module = module, sha256 = "0".repeat(64))),
            ),
        )
    }

    private fun coverageWorkspaces(): Set<Path> = Files.list(
        Path.of(System.getProperty("java.io.tmpdir"))
    ).use { files ->
        files.iterator().asSequence()
            .filter { path -> path.fileName.toString().startsWith("usvm-ts-fast-check-coverage-") }
            .toHashSet()
    }

    private fun jsString(value: String): String = "'${value.replace("\\", "\\\\").replace("'", "\\'")}'"
}
