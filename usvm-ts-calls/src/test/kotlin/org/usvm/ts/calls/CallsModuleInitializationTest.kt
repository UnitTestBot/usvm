package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CallsModuleInitializationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `literal modules preserve aliases and initialize once within a scenario and reset between samples`() {
        val harness = fixture()
        val instrumentation = CallsClassSourceInstrumentation.create(
            sourceRoot = directory,
            sourceFile = "Cache.ts",
            className = "Cache",
        )
        instrumentation.validateSurface(harness.surface)
        val classOffset = Files.readString(directory.resolve("Cache.ts")).indexOf("export class Cache")
        assertTrue(instrumentation.universe.statements.all { it.startOffset >= classOffset })
        val admission = callsClassSourceAdmissionFailures(harness, CallsBuildIdentity.nativeFrontendRevision)

        assertEquals(emptyList(), admission)

        val repeated = harness.scenarios.indexOfFirst { it.memberIds == listOf("advance", "advance") }
        OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[repeated],
            observationFields = harness.observations[repeated],
        ).use { replayer ->
            val first = replayer.replay(inputs = emptyList(), timeoutMillis = 20_000)
            val second = replayer.replay(inputs = emptyList(), timeoutMillis = 20_000)

            assertEquals(number(value = 10), first.observation.values["state_0_count"])
            assertEquals(number(value = 10), first.observation.values["result_0"])
            assertEquals(number(value = 11), first.observation.values["result_1"])
            assertEquals(number(value = 12), first.observation.values["state_2_count"])
            assertEquals(first.observation, second.observation)
        }

        val result = CurrentTsCallsClassCoverageEngine().search(
            CallsClassSearchRequest(
                harness = harness,
                profile = CallsExperimentProfile.EMPTY_STOP,
                frozenModelIds = emptySet(),
                expectedNativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
                seed = 1,
                budget = 8.seconds,
                timeQuantum = 100.milliseconds,
                solverQueryLimit = 1.seconds,
                solverType = SolverType.YICES,
                candidateCap = 30,
                requireEntryPointWitness = true,
            ),
        )
        val candidates = result.search.candidates.filter { it.observation != null }
        assertEquals(emptyList(), result.search.extractionFailures)
        assertEquals(
            harness.scenarios.map { it.id }.toSet(),
            candidates.map { it.entryPointId }.toSet(),
            result.toString(),
        )
        for (candidate in candidates) {
            val index = harness.scenarios.indexOfFirst { it.id == candidate.entryPointId }
            val replay = OriginalTypeScriptClassReplayer(
                sourceRoot = directory,
                function = harness.functions[index],
                observationFields = harness.observations[index],
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = 20_000) }
            val instrumented = OriginalTypeScriptClassReplayer(
                sourceRoot = directory,
                function = harness.functions[index],
                observationFields = harness.observations[index],
                sourceOverride = instrumentation.instrumentedSource,
                overrideSourceFile = "Cache.ts",
                counterGlobalName = instrumentation.hitGlobalName,
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = 20_000) }
            val agreement = compareCallsClassObservations(
                symbolic = candidate.observation,
                original = replay.observation,
                instrumented = instrumented.observation,
            )
            val coverage = callsClassNativeCoverageAcceptance(replay, instrumented, instrumentation.universe)

            assertTrue(agreement.accepted, "$candidate / $replay / $instrumented / $agreement")
            assertTrue(coverage.accepted, coverage.toString())
            val scenario = harness.scenarios[index]
            for (memberIndex in scenario.memberIds.indices) {
                if (scenario.memberIds[memberIndex] == "same") {
                    val observation = assertNotNull(candidate.observation)

                    assertEquals(
                        expected = JsConcreteValue.Boolean(value = true),
                        actual = observation.values["result_$memberIndex"],
                    )
                }
            }
        }
    }

    private fun fixture(): CallsClassPreparedHarness {
        Files.writeString(
            directory.resolve("data.ts"),
            """
                const initial = 10;
                const items = [initial];
                export const box = { items: items };
                export const alias = box;
                export const rows = [items, items];
            """.trimIndent(),
        )
        Files.writeString(directory.resolve("barrel.ts"), "export { box, alias, rows } from './data';")
        Files.writeString(
            directory.resolve("Cache.ts"),
            """
                import { box, alias, rows } from './barrel';
                const local = box;
                export class Cache {
                  count: number;
                  constructor() { this.count = box.items[0]; }
                  advance(): number {
                    const old = box.items[0];
                    alias.items[0] = old + 1;
                    this.count = local.items[0];
                    return old;
                  }
                  same(): boolean {
                    return alias === box && local === box && rows[0] === box.items && rows[1] === box.items;
                  }
                }
            """.trimIndent(),
        )
        return prepareCallsClassHarness(
            sourceRoot = directory,
            project = CallsProjectCase(
                projectId = "module-initialization",
                revision = "0".repeat(40),
                sourceRoot = ".",
                development = true,
                functions = emptyList(),
            ),
            surface = CallsClassSurface(
                sourceFile = "Cache.ts",
                exportName = "Cache",
                constructorInputs = emptyList(),
                members = listOf(
                    CallsClassMember(
                        id = "advance",
                        name = "advance",
                        kind = CallsClassMemberKind.METHOD,
                        inputs = emptyList(),
                        resultDomain = NumberDomain(),
                    ),
                    CallsClassMember(
                        id = "same",
                        name = "same",
                        kind = CallsClassMemberKind.METHOD,
                        inputs = emptyList(),
                        resultDomain = BooleanDomain,
                    ),
                ),
                stateFields = mapOf("count" to NumberDomain()),
            ),
            maxSequenceLength = 2,
        )
    }

    private fun number(value: Int) = JsConcreteValue.number(value.toDouble())
}
