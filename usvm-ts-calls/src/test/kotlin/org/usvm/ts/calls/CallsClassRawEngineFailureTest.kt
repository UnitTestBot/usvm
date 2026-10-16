package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CallsClassRawEngineFailureTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `suppressed interpreter failure persists as a failed prefix instead of UNSAT or a return`() {
        val source = directory.resolve("Target.ts")
        Files.writeString(source, "export class Target { run(): number { return 7; } }")
        val file = loadEtsFileAutoConvert(path = source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(projectFiles = listOf(file))
        val method = scene.projectClasses.single { it.name == "Target" }.methods.single { it.name == "run" }
        val raw = ClassRawAnalysisObserver(sourceFile = "Target.ts", universe = emptyList(), output = directory)
        val observer = object : TsInterpreterObserver {
            override fun onReturnStatement(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsReturnStmt,
                scope: TsStepScope,
            ) {
                if (stmt.location.method != method) return

                error("Injected raw-journal step failure")
            }
        }

        val result = TsMachine(
            scene = scene,
            options = UMachineOptions(
                solverType = SolverType.YICES,
                stateCollectionStrategy = StateCollectionStrategy.ALL,
                stopOnCoverage = 0,
                timeout = 5.seconds,
            ),
            tsOptions = TsOptions(),
            observer = observer,
            machineObserverFactory = { raw },
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }
        val manifest = manifest(source = source)
        val analysis = raw.result(manifest = manifest, analysisElapsedMillis = 0)
        val snapshots = GZIPInputStream(Files.newInputStream(directory.resolve(analysis.stateSnapshots.path)))
            .bufferedReader().use { reader ->
                reader.lineSequence().map { CallsExperimentJson.json.decodeFromString<CallsClassRawStateSnapshot>(it) }
                    .toList()
            }

        assertTrue(result.engineFailed)
        assertFalse(result.runtimeLimited)
        assertTrue(result.states.isEmpty())
        assertEquals(expected = 1L, actual = analysis.engineFailureStates)
        assertEquals(expected = 0L, actual = analysis.returnedStates)
        assertEquals(expected = 0L, actual = analysis.unsupportedTerminalStates)
        assertEquals(expected = 0L, actual = analysis.unreachableTerminalStates)
        assertTrue(analysis.stateSnapshotFailures.isEmpty())
        val snapshot = snapshots.single()
        assertEquals(expected = "ENGINE_FAILURE", actual = snapshot.outcome)
        assertTrue(snapshot.callStack.isNotEmpty())
        assertTrue(snapshot.engineFailureReason?.contains("Injected raw-journal step failure") == true)
        assertNull(snapshot.unsupportedReason)
        assertNull(snapshot.symbolicResultId)
    }

    private fun manifest(source: Path) = CallsClassStartManifest(
        toolRevision = "fixture",
        nativeFrontendRevision = "fixture",
        ledgerSha256 = "b".repeat(n = 64),
        expectedClassCount = 1,
        classes = listOf(
            CallsClassStartCase(
                classId = "target",
                identity = "fixture@Target",
                projectRevision = "a".repeat(n = 40),
                sourceRoot = directory.toString(),
                sourceFile = "Target.ts",
                className = "Target",
                inputSha256 = mapOf("Target.ts" to callsArtifactSha256(Files.readAllBytes(source))),
            ),
        ),
    )
}
