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
        checkFailure(retainRawStateDiagnostics = true, registerBindings = true)
    }

    @Test
    fun `secondary extraction failure preserves original engine reason with raw dumps disabled`() {
        checkFailure(retainRawStateDiagnostics = false, registerBindings = false)
    }

    private fun checkFailure(retainRawStateDiagnostics: Boolean, registerBindings: Boolean) {
        val source = directory.resolve("Target.ts")
        Files.writeString(source, "export class Target { run(): number { return 7; } }")
        val file = loadEtsFileAutoConvert(path = source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(projectFiles = listOf(file))
        val method = scene.projectClasses.single { it.name == "Target" }.methods.single { it.name == "run" }
        val raw = ClassRawAnalysisObserver(
            sourceFile = "Target.ts",
            universe = emptyList(),
            output = directory,
            retainRawStateDiagnostics = retainRawStateDiagnostics,
            profile = CallsExperimentProfile.FROZEN_STOP,
        )
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
        ).use { machine ->
            machine.analyzeWithMetadata(
                methods = listOf(method),
                configureInitialState = { entry, state ->
                    // Deliberately omit bindings to exercise a secondary journal extraction failure.
                    if (registerBindings) raw.registerInitialState(method = entry, state = state)
                },
            )
        }
        val manifest = manifest(source = source)
        val analysis = raw.result(manifest = manifest, analysisElapsedMillis = 0)
        assertTrue(result.engineFailed)
        assertFalse(result.runtimeLimited)
        assertTrue(result.states.isEmpty())
        assertEquals(expected = 1L, actual = analysis.engineFailureStates)
        assertEquals(expected = 0L, actual = analysis.returnedStates)
        assertEquals(expected = 0L, actual = analysis.unsupportedTerminalStates)
        assertEquals(expected = 0L, actual = analysis.unreachableTerminalStates)
        assertTrue(analysis.stateSnapshotFailures.isEmpty())
        if (retainRawStateDiagnostics) {
            val snapshotPath = directory.resolve(checkNotNull(analysis.stateSnapshots).path)
            val snapshots = GZIPInputStream(Files.newInputStream(snapshotPath)).bufferedReader().use { reader ->
                reader.lineSequence().map { CallsExperimentJson.json.decodeFromString<CallsClassRawStateSnapshot>(it) }
                    .toList()
            }
            val snapshot = snapshots.single()
            assertEquals(expected = "ENGINE_FAILURE", actual = snapshot.outcome)
            assertTrue(snapshot.callStack.isNotEmpty())
            assertTrue(snapshot.engineFailureReason?.contains("Injected raw-journal step failure") == true)
            assertNull(snapshot.unsupportedReason)
            assertNull(snapshot.symbolicResultId)
        } else {
            assertNull(analysis.stateSnapshots)
            assertNull(analysis.symbolicExpressions)
            val lines = Files.readAllLines(directory.resolve(checkNotNull(analysis.inputExtractionFailures).path))
            val failure = lines.map {
                CallsExperimentJson.json.decodeFromString<CallsClassInputExtractionFailure>(it)
            }.single()

            assertTrue(failure.diagnostic.contains("Missing original entry bindings"))
            val original = checkNotNull(failure.originalEngineFailureReason)
            assertTrue(original.contains("IllegalStateException: Injected raw-journal step failure"))
            assertTrue(original.contains("statement="))
            assertTrue(original.contains("CallsClassRawEngineFailureTest"))
            assertTrue(original.length <= 2048)
            assertTrue(checkNotNull(failure.currentMethod).contains("run"))
            assertTrue(checkNotNull(failure.currentSourceFile).contains("Target"))
            assertTrue(checkNotNull(failure.statementIndex) >= 0)
        }
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
