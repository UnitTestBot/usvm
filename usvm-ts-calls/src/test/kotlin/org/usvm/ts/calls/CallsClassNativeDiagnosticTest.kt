package org.usvm.ts.calls

import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallsClassNativeDiagnosticTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `native numeric throws retain the actual primitive value`() {
        val harness = fixture(constructorBody = "throw 7;")

        val replay = replay(harness)

        assertEquals(CallsCoverageCompletion.THREW, replay.observation.completion)
        assertFalse(replay.observation.representable)
        val expectedValue = CallsExperimentJson.json.encodeToString<JsConcreteValue>(JsConcreteValue.number(7.0))
        assertEquals("$thrownPrefix; thrown value=$expectedValue", replay.observation.diagnostic)
    }

    @Test
    fun `native string throws retain the actual string value`() {
        val harness = fixture(constructorBody = "throw 'actual thrown string';")

        val replay = replay(harness)

        assertEquals(CallsCoverageCompletion.THREW, replay.observation.completion)
        assertFalse(replay.observation.representable)
        val expectedValue = CallsExperimentJson.json.encodeToString<JsConcreteValue>(
            JsConcreteValue.String("actual thrown string"),
        )
        assertEquals("$thrownPrefix; thrown value=$expectedValue", replay.observation.diagnostic)
    }

    @Test
    fun `native errors retain the actual name and message`() {
        val harness = fixture(constructorBody = "throw new TypeError('actual native message');")

        val replay = replay(harness)

        assertEquals(CallsCoverageCompletion.THREW, replay.observation.completion)
        assertFalse(replay.observation.representable)
        assertEquals(
            "$thrownPrefix; exception name=\"TypeError\", message=\"actual native message\"",
            replay.observation.diagnostic,
        )
    }

    @Test
    fun `repeated loop coverage preserves native snapshots and contains only unique statement keys`() {
        val harness = fixture(constructorBody = "for (let i = 0; i < 20000; i++) { this.count++; }")
        val instrumentation = CallsClassSourceInstrumentation.create(
            sourceRoot = directory,
            sourceFile = "NativeOutcome.ts",
            className = "NativeOutcome",
        )

        val original = replay(harness)
        val instrumented = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions.single(),
            observationFields = harness.observations.single(),
            sourceOverride = instrumentation.instrumentedSource,
            overrideSourceFile = "NativeOutcome.ts",
            counterGlobalName = instrumentation.hitGlobalName,
        ).use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

        assertTrue(original.observation.representable, original.observation.diagnostic)
        assertEquals(JsConcreteValue.number(20_000.0), original.observation.values["state_0_count"])
        assertEquals(original.observation, instrumented.observation)
        val coveredIds = instrumentation.universe.checkedCoveredIds(instrumented.coveredStatementKeys)
        assertEquals(instrumentation.universe.statements.size, coveredIds.size)
    }

    private fun fixture(constructorBody: String): CallsClassPreparedHarness {
        Files.writeString(
            directory.resolve("NativeOutcome.ts"),
            """
                export class NativeOutcome {
                  count: number = 0;
                  constructor() { $constructorBody }
                }
            """.trimIndent(),
        )
        val surface = CallsClassSurface(
            sourceFile = "NativeOutcome.ts",
            exportName = "NativeOutcome",
            constructorInputs = emptyList(),
            members = emptyList(),
            stateFields = mapOf("count" to NumberDomain()),
        )
        val project = CallsProjectCase(
            projectId = "native-diagnostic-fixture",
            revision = "0".repeat(40),
            sourceRoot = ".",
            development = true,
            functions = emptyList(),
        )

        return prepareCallsClassHarness(directory, project, surface, maxSequenceLength = 2)
    }

    private fun replay(harness: CallsClassPreparedHarness): CallsClassReplayObservation =
        OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions.single(),
            observationFields = harness.observations.single(),
        ).use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

    private val thrownPrefix = "Thrown class scenarios have no complete return/state snapshot"
}
