package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CallsClassUnknownCallJournalTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `actual repeated source calls retain ordered model and residual decisions in serialized search`() {
        val harness = prepareFixture(
            source = """
                export class Telemetry {
                  value: number = 0;
                  constructor(input: number) {
                    for (let index = 0; index < 2; index++) {
                      this.value = Math.floor(input);
                    }
                  }
                }
            """.trimIndent(),
            inputs = listOf(PropertyInput(name = "input", domain = IntegerDomain(min = 0, max = 3))),
            stateFields = mapOf("value" to NumberDomain()),
        )

        val modeled = search(harness, profile = CallsExperimentProfile.FROZEN_FRESH, models = setOf("ts.math.floor"))
        val residual = search(harness, profile = CallsExperimentProfile.EMPTY_FRESH, models = emptySet())

        assertTrue(modeled.search.candidates.isNotEmpty(), modeled.toString())
        assertTrue(residual.search.candidates.isNotEmpty(), residual.toString())
        for (result in listOf(modeled, residual)) {
            assertTrue(result.unknownCalls.size >= 2, result.toString())
            assertEquals(result.unknownCallEvents, result.unknownCalls.size)
            assertEquals((1..result.unknownCalls.size).toList(), result.unknownCalls.map { it.eventIndex })
            val sites = result.unknownCalls.groupingBy { it.callSite }.eachCount()
            assertTrue(sites.values.any { it >= 2 }, result.toString())
            assertTrue(result.unknownCalls.all { it.callSite.sourcePath.endsWith("Telemetry.ts") }, result.toString())
            assertTrue(result.unknownCalls.all { it.callerMethodSignature.contains("Telemetry::constructor") })
            assertTrue(result.unknownCalls.all { it.callee.name == "floor" })
            assertTrue(result.unknownCalls.all { it.failureReason == "PARTIAL_APPROXIMATION" })
            assertNotNull(result.unknownCalls.first().callSite.startOffset)

            val encoded = CallsExperimentJson.json.encodeToString(result)
            assertEquals(result, CallsExperimentJson.json.decodeFromString<CallsClassSearchResult>(encoded))
            val legacy = JsonObject(
                CallsExperimentJson.json.parseToJsonElement(encoded).jsonObject.filterKeys { it != "unknownCalls" },
            )
            val decodedLegacy = CallsExperimentJson.json.decodeFromString<CallsClassSearchResult>(legacy.toString())
            assertEquals(emptyList(), decodedLegacy.unknownCalls)
            assertEquals(result.unknownCallEvents, decodedLegacy.unknownCallEvents)
        }
        for (event in modeled.unknownCalls) {
            assertEquals(CallsUnknownCallDecisionKind.MODEL_APPLIED, event.decision)
            assertEquals("MODEL_APPLIED", event.outcome)
            assertEquals("ts.math.floor", event.modelId)
            assertNull(event.residualPolicy)
        }
        for (event in residual.unknownCalls) {
            assertEquals(CallsUnknownCallDecisionKind.RESIDUAL_FALLBACK, event.decision)
            assertEquals("FRESH_SYMBOLIC_RETURN", event.outcome)
            assertNull(event.modelId)
            assertEquals("FRESH_SYMBOLIC_RETURN", event.residualPolicy)
        }
    }

    @Test
    fun `symbolic thrown diagnostic retains actual exception type and expression`() {
        val harness = prepareFixture(
            source = """
                export class Telemetry {
                  constructor() { throw 7; }
                }
            """.trimIndent(),
            inputs = emptyList(),
            stateFields = emptyMap(),
        )

        val result = search(harness, profile = CallsExperimentProfile.EMPTY_STOP, models = emptySet())

        val observation = assertNotNull(result.search.candidates.single().observation)
        assertEquals(CallsCoverageCompletion.THREW, observation.completion)
        assertFalse(observation.representable)
        assertEquals(emptyMap(), observation.values)
        val diagnostic = assertNotNull(observation.diagnostic)
        assertTrue(diagnostic.contains("exception type=number"), diagnostic)
        assertTrue(diagnostic.contains("value=(fp "), diagnostic)
        assertFalse(compareCallsClassObservations(observation, observation, observation).accepted)
    }

    private fun prepareFixture(
        source: String,
        inputs: List<PropertyInput>,
        stateFields: Map<String, org.usvm.ts.pbt.model.PropertyDomain>,
    ): CallsClassPreparedHarness {
        Files.writeString(directory.resolve("Telemetry.ts"), source)
        val surface = CallsClassSurface(
            sourceFile = "Telemetry.ts",
            exportName = "Telemetry",
            constructorInputs = inputs,
            members = emptyList(),
            stateFields = stateFields,
        )
        val project = CallsProjectCase(
            projectId = "fixture",
            revision = "0".repeat(40),
            sourceRoot = ".",
            development = false,
            functions = emptyList(),
        )

        return prepareCallsClassHarness(directory, project, surface, maxSequenceLength = 1)
    }

    private fun search(
        harness: CallsClassPreparedHarness,
        profile: CallsExperimentProfile,
        models: Set<String>,
    ): CallsClassSearchResult = CurrentTsCallsClassCoverageEngine().search(
        CallsClassSearchRequest(
            harness = harness,
            profile = profile,
            frozenModelIds = models,
            expectedNativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
            seed = 1,
            budget = 2.seconds,
            timeQuantum = 100.milliseconds,
            solverQueryLimit = 1.seconds,
            solverType = SolverType.YICES,
            candidateCap = 10,
            requireEntryPointWitness = true,
        ),
    )
}
