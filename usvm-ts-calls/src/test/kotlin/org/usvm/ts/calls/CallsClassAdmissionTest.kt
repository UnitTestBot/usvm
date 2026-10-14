package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.contains
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallsClassAdmissionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `class admission does not require a symbolic pilot to complete any hashed method scenario`() {
        val harness = fixture()
        val instrumentation = instrumentation()
        val sourceFailures = callsClassSourceAdmissionFailures(harness, CallsBuildIdentity.nativeFrontendRevision)

        val smoke = callsClassNativeConstructorSmoke(harness, instrumentation, seed = 17, timeoutMillis = 20_000)
        val repeated = callsClassNativeConstructorSmoke(harness, instrumentation, seed = 17, timeoutMillis = 20_000)
        val result = admission(harness, sourceFailures, smoke)

        assertEquals(emptyList(), sourceFailures)
        assertEquals(CallsClassNativeSmokeStatus.VERIFIED, smoke.status)
        val original = assertNotNull(smoke.original)
        assertEquals(original.inputs, assertNotNull(repeated.original).inputs)
        assertEquals(original.observation, repeated.original.observation)
        assertTrue(original.inputs.single() in harness.surface.constructorInputs.single().domain)
        assertTrue(result.eligible)
        assertEquals(true, result.primaryInferenceEligible)
        assertEquals(false, result.diagnosticReady)
        assertEquals(CallsCoverageSearchStatus.TIMEOUT, result.search.search.status)
        assertEquals(harness.scenarios.map { it.id }, result.missingRequiredScenarios)
        assertEquals(harness.scenarios.size, result.missingRequiredScenarios.size)
    }

    @Test
    fun `native sampler rejects seed overflow instead of truncating the frozen seed`() {
        val harness = fixture()

        OriginalTypeScriptClassReplayer(
            directory,
            harness.functions.first(),
            harness.observations.first(),
        ).use { replayer ->
            assertFailsWith<IllegalArgumentException> {
                replayer.sample(seed = Int.MAX_VALUE.toLong() + 1, timeoutMillis = 20_000)
            }
            assertFailsWith<IllegalArgumentException> {
                replayer.sample(seed = Int.MIN_VALUE.toLong() - 1, timeoutMillis = 20_000)
            }
        }
    }

    @Test
    fun `pilot native corruption remains an acquisition failure without selecting the class population`() {
        val harness = fixture()
        val originalInstrumentation = instrumentation()
        val modified = originalInstrumentation.instrumentedSource.replace(
            "return key.length === this.size;",
            "return false;",
        )
        val changed = originalInstrumentation.copy(
            instrumentedSource = modified,
            instrumentedSha256 = callsClassSourceSha256(modified.toByteArray(Charsets.UTF_8)),
        )
        val smoke = callsClassNativeConstructorSmoke(harness, changed, seed = 17, timeoutMillis = 20_000)
        val inputs = listOf(JsConcreteValue.String("aa"), JsConcreteValue.String("aa"))

        val original = OriginalTypeScriptClassReplayer(directory, harness.functions[1], harness.observations[1]).use {
            it.replay(inputs = inputs, timeoutMillis = 20_000)
        }
        val instrumented = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[1],
            observationFields = harness.observations[1],
            sourceOverride = changed.instrumentedSource,
            overrideSourceFile = "SlowMap.ts",
            counterGlobalName = changed.hitGlobalName,
        ).use { it.replay(inputs = inputs, timeoutMillis = 20_000) }
        val replay = CallsClassReplayEvent(
            index = 0,
            scenarioId = harness.scenarios[1].id,
            original = original,
            instrumented = instrumented,
            oracle = compareCallsClassObservations(
                original.observation,
                original.observation,
                instrumented.observation,
            ),
            nativeCoverage = callsClassNativeCoverageAcceptance(original, instrumented, changed.universe),
        )
        val failures = callsClassPilotNativeFailures(listOf(replay), changed.universe)
        val baseline = admission(harness, sourceFailures = emptyList(), smoke = smoke)
        val result = callsClassPreflightResult(
            classCase = classCase(harness),
            preparedSha256 = baseline.preparedSha256,
            toolRevision = baseline.toolRevision,
            search = baseline.search,
            replays = listOf(replay),
            missingScenarios = baseline.missingRequiredScenarios,
            pilotDiagnostics = emptyList(),
            sourceAdmissionFailures = emptyList(),
            nativeSmoke = smoke,
            nativeInfrastructureFailures = failures,
        )

        assertEquals(CallsClassNativeSmokeStatus.VERIFIED, smoke.status)
        assertTrue(failures.isNotEmpty(), replay.toString())
        assertFalse(replay.oracle.accepted)
        assertTrue(result.eligible)
        assertEquals(true, result.primaryInferenceEligible)
        assertEquals(false, result.diagnosticReady)
        assertEquals(failures, result.nativeInfrastructureFailures)
    }

    @Test
    fun `changed native constructor state rejects the observation mechanism independently of pilot coverage`() {
        val harness = fixture()
        val originalInstrumentation = instrumentation()
        val modified = originalInstrumentation.instrumentedSource.replace(
            "this.size = initial.length;",
            "this.size = initial.length + 1;",
        )
        val changed = originalInstrumentation.copy(
            instrumentedSource = modified,
            instrumentedSha256 = callsClassSourceSha256(modified.toByteArray(Charsets.UTF_8)),
        )

        val smoke = callsClassNativeConstructorSmoke(harness, changed, seed = 17, timeoutMillis = 20_000)
        val result = admission(harness, sourceFailures = emptyList(), smoke = smoke)

        assertEquals(CallsClassNativeSmokeStatus.INVALID, smoke.status)
        assertFalse(result.eligible)
        assertEquals(false, result.primaryInferenceEligible)
        val changedState = smoke.diagnostic?.contains(
            "Instrumentation changed native returned values or receiver state",
        ) == true
        assertTrue(changedState)
    }

    @Test
    fun `instrumentation changing native observability is invalid even with unchanged frozen inputs`() {
        val harness = fixture()
        val originalInstrumentation = instrumentation()
        val modified = originalInstrumentation.instrumentedSource.replace(
            "this.size = initial.length;",
            "this.size = 'outside-the-number-schema' as any;",
        )
        val changed = originalInstrumentation.copy(
            instrumentedSource = modified,
            instrumentedSha256 = callsClassSourceSha256(modified.toByteArray(Charsets.UTF_8)),
        )

        val smoke = callsClassNativeConstructorSmoke(harness, changed, seed = 17, timeoutMillis = 20_000)
        val original = assertNotNull(smoke.original)
        val instrumented = assertNotNull(smoke.instrumented)
        val sameCompletionIncomplete = instrumented.copy(
            observation = instrumented.observation.copy(completion = original.observation.completion),
        )

        assertEquals(original.inputs, instrumented.inputs)
        assertTrue(original.observation.representable)
        assertFalse(instrumented.observation.representable)
        assertEquals(CallsClassNativeSmokeStatus.INVALID, smoke.status)
        assertEquals(
            "Instrumentation changed native observability or observation schema",
            callsClassNativeInfrastructureFailure(original, sameCompletionIncomplete),
        )
    }

    @Test
    fun `lawful incomplete native constructor observation remains inconclusive without silently excluding raw cells`() {
        val harness = fixture(source = source.replace("this.size = initial.length;", "throw 7;"))

        val smoke = callsClassNativeConstructorSmoke(harness, instrumentation(), seed = 17, timeoutMillis = 20_000)
        val result = admission(harness, sourceFailures = emptyList(), smoke = smoke)

        assertEquals(CallsClassNativeSmokeStatus.INCONCLUSIVE, smoke.status)
        assertTrue(result.eligible)
        assertEquals(false, result.primaryInferenceEligible)
        assertNotNull(smoke.diagnostic)
    }

    @Test
    fun `source closure preparation failure is a hard admission gate independent of pilot outcomes`() {
        val harness = fixture(source = "const cache = [Math.random()];\n" + source)
        val failures = callsClassSourceAdmissionFailures(harness, CallsBuildIdentity.nativeFrontendRevision)

        val result = admission(harness, sourceFailures = failures, smoke = null)

        assertTrue(failures.any { it.contains("TOP_LEVEL_INITIALIZATION_UNSUPPORTED") }, failures.toString())
        assertFalse(result.eligible)
        assertEquals(false, result.primaryInferenceEligible)
        assertEquals(failures, result.sourceAdmissionFailures)
    }

    @Test
    fun `symbolic fresh-result disagreement remains a pilot outcome with strict candidate rejection`() {
        val harness = fixture()
        val smoke = callsClassNativeConstructorSmoke(harness, instrumentation(), seed = 17, timeoutMillis = 20_000)
        val original = assertNotNull(smoke.original)
        val instrumented = assertNotNull(smoke.instrumented)
        val symbolic = original.observation.copy(values = mapOf("state_0_size" to JsConcreteValue.number(-1.0)))
        val oracle = compareCallsClassObservations(symbolic, original.observation, instrumented.observation)
        val baseline = admission(harness, sourceFailures = emptyList(), smoke = smoke)
        val replay = CallsClassReplayEvent(
            index = 0,
            scenarioId = smoke.scenarioId,
            original = original,
            instrumented = instrumented,
            oracle = oracle,
            nativeCoverage = callsClassNativeCoverageAcceptance(original, instrumented, instrumentation().universe),
        )
        val candidate = CallsCoverageCandidate(
            emittedAtMillis = 1,
            emittedAtStep = 1,
            inputs = original.inputs,
            newSymbolicStatements = 1,
            completion = CallsCoverageCompletion.RETURNED,
            entryPointId = smoke.scenarioId,
            observation = symbolic,
        )
        val pilot = baseline.search.copy(
            search = baseline.search.search.copy(
                candidates = listOf(candidate),
                selectedStates = 1,
            ),
        )

        val result = callsClassPreflightResult(
            classCase = classCase(harness),
            preparedSha256 = baseline.preparedSha256,
            toolRevision = baseline.toolRevision,
            search = pilot,
            replays = listOf(replay),
            missingScenarios = baseline.missingRequiredScenarios,
            pilotDiagnostics = listOf("Symbolic and original returned values or receiver state disagree"),
            sourceAdmissionFailures = emptyList(),
            nativeSmoke = smoke,
            nativeInfrastructureFailures = listOfNotNull(callsClassNativeInfrastructureFailure(original, instrumented)),
        )

        assertFalse(oracle.accepted)
        assertTrue(replay.nativeCoverage.accepted)
        assertTrue(result.eligible)
        assertEquals(true, result.primaryInferenceEligible)
        assertEquals(false, result.diagnosticReady)
        assertEquals(listOf(replay), result.replays)
    }

    @Test
    fun `documented frozen correctness frontier excludes primary inference while preserving raw diagnostics`() {
        val harness = fixture()
        val smoke = callsClassNativeConstructorSmoke(harness, instrumentation(), seed = 17, timeoutMillis = 20_000)
        val failure = CallsClassCorrectnessFrontierFailure(
            reason = "Known constructor lowering defect",
            provenance = "Pinned frontend regression and independently replayed counterexample",
        )
        val classCase = classCase(harness).copy(correctnessFrontierFailures = listOf(failure))

        val result = admission(harness, sourceFailures = emptyList(), smoke = smoke, selectedCase = classCase)

        assertTrue(result.eligible)
        assertEquals(false, result.primaryInferenceEligible)
        assertEquals(listOf(failure), result.correctnessFrontierFailures)
    }

    @Test
    fun `new admission serializes explicitly and legacy strict preflight remains version zero`() {
        val harness = fixture()
        val smoke = callsClassNativeConstructorSmoke(harness, instrumentation(), seed = 17, timeoutMillis = 20_000)
        val result = admission(harness, sourceFailures = emptyList(), smoke = smoke)

        val encoded = CallsExperimentJson.json.encodeToString(result)
        val decoded = CallsExperimentJson.json.decodeFromString<CallsClassPreflightResult>(encoded)
        val addedFields = setOf(
            "admissionVersion",
            "sourceAdmissionFailures",
            "nativeInfrastructureFailures",
            "nativeSmoke",
            "diagnosticReady",
            "primaryInferenceEligible",
            "correctnessFrontierFailures",
        )
        val legacy = JsonObject(
            CallsExperimentJson.json.parseToJsonElement(encoded).jsonObject.filterKeys {
                it !in addedFields
            },
        )
        val decodedLegacy = CallsExperimentJson.json.decodeFromString<CallsClassPreflightResult>(legacy.toString())

        assertEquals(result, decoded)
        assertEquals(1, decoded.admissionVersion)
        assertEquals(0, decodedLegacy.admissionVersion)
        assertEquals(null, decodedLegacy.primaryInferenceEligible)
        assertEquals(null, decodedLegacy.nativeSmoke)
    }

    private fun fixture(source: String = this.source): CallsClassPreparedHarness {
        Files.writeString(directory.resolve("SlowMap.ts"), source)
        val surface = CallsClassSurface(
            sourceFile = "SlowMap.ts",
            exportName = "SlowMap",
            constructorInputs = listOf(
                PropertyInput(name = "initial", domain = StringDomain(minLength = 2, maxLength = 6)),
            ),
            members = listOf(
                CallsClassMember(
                    id = "has",
                    name = "has",
                    kind = CallsClassMemberKind.METHOD,
                    inputs = listOf(PropertyInput(name = "key", domain = StringDomain(minLength = 2, maxLength = 6))),
                    resultDomain = BooleanDomain,
                ),
            ),
            stateFields = mapOf("size" to NumberDomain()),
        )
        val project = CallsProjectCase(
            projectId = "fixture",
            revision = "0".repeat(40),
            sourceRoot = ".",
            development = true,
            functions = emptyList(),
        )

        return prepareCallsClassHarness(directory, project, surface, maxSequenceLength = 2)
    }

    private fun instrumentation() = CallsClassSourceInstrumentation.create(
        sourceRoot = directory,
        sourceFile = "SlowMap.ts",
        className = "SlowMap",
    )

    private fun classCase(harness: CallsClassPreparedHarness) = CallsClassCase(
        classId = "slow-map",
        projectId = "fixture",
        revision = "0".repeat(40),
        sourceRoot = directory.toString(),
        sourceSha256 = mapOf(
            "SlowMap.ts" to callsClassSourceSha256(Files.readAllBytes(directory.resolve("SlowMap.ts"))),
        ),
        surface = harness.surface,
    )

    private fun admission(
        harness: CallsClassPreparedHarness,
        sourceFailures: List<String>,
        smoke: CallsClassNativeSmokeResult?,
        selectedCase: CallsClassCase = classCase(harness),
    ) = callsClassPreflightResult(
        classCase = selectedCase,
        preparedSha256 = "a".repeat(64),
        toolRevision = "b".repeat(40),
        search = CallsClassSearchResult(
            search = CallsCoverageSearchResult(
                status = CallsCoverageSearchStatus.TIMEOUT,
                candidates = emptyList(),
                selectedStates = 0,
                executedSteps = 10,
                stepsWithinBudget = 10,
                extractionFailures = emptyList(),
                candidateCapReached = false,
                preparationElapsedMillis = 0,
                machineSetupElapsedMillis = 0,
                searchElapsedMillis = 120_000,
                machineTeardownElapsedMillis = 0,
            ),
            scenarios = emptyList(),
            visits = emptyList(),
            unknownCallEvents = 0,
        ),
        replays = emptyList(),
        missingScenarios = harness.scenarios.map { it.id },
        pilotDiagnostics = listOf("Symbolic pilot reached the time budget", "No scenario witnesses"),
        sourceAdmissionFailures = sourceFailures,
        nativeSmoke = smoke,
        nativeInfrastructureFailures = emptyList(),
    )

    private val source = """
        export class SlowMap {
          size: number;
          constructor(initial: string) { this.size = initial.length; }
          has(key: string): boolean { return key.length === this.size; }
        }
    """.trimIndent()
}
