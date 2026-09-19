package org.usvm.ts.pbt.calls

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallsExperimentTest {
    @Test
    fun `runner rotates profiles and records symbolic and replay outcomes separately`(@TempDir directory: Path) {
        val requests = mutableListOf<CallsSymbolicSearchRequest>()
        val engine = CallsSymbolicEngine { request ->
            requests += request

            when (request.profile) {
                CallsExperimentProfile.EMPTY_STOP -> result(status = CallsSymbolicStatus.UNREACHED)
                CallsExperimentProfile.EMPTY_FRESH -> result(
                    status = CallsSymbolicStatus.REACHED,
                    inputs = listOf(JsConcreteValue.Boolean(false)),
                )

                CallsExperimentProfile.FROZEN_STOP -> result(
                    status = CallsSymbolicStatus.REACHED,
                    inputs = listOf(JsConcreteValue.Boolean(true)),
                )

                CallsExperimentProfile.FROZEN_FRESH -> result(status = CallsSymbolicStatus.UNSUPPORTED)
            }
        }
        val replayer = CallsTargetReplayer { _, _, inputs, _, _ ->
            val input = inputs.single() as JsConcreteValue.Boolean

            CallsSourceReplayResult(
                status = if (input.value) CallsReplayStatus.CONFIRMED else CallsReplayStatus.REJECTED,
            )
        }
        val rawOutput = directory.resolve("raw/results.jsonl")

        CallsExperimentRunner(symbolicEngine = engine, targetReplayer = replayer).run(
            manifest = manifest(sourceRoot = ".", seeds = listOf(1L)),
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )

        val records = readRecords(rawOutput)
        val metadata = records.filterIsInstance<CallsRunMetadata>().single()
        val results = records.filterIsInstance<CallsTargetResult>()

        assertEquals(1, metadata.commonEligibleTargets)
        assertEquals(
            listOf(
                CallsExperimentProfile.EMPTY_FRESH,
                CallsExperimentProfile.FROZEN_STOP,
                CallsExperimentProfile.FROZEN_FRESH,
                CallsExperimentProfile.EMPTY_STOP,
            ),
            requests.map(CallsSymbolicSearchRequest::profile),
        )
        assertEquals(requests.map(CallsSymbolicSearchRequest::profile), results.map(CallsTargetResult::profile))

        val emptyFresh = results.single { result -> result.profile == CallsExperimentProfile.EMPTY_FRESH }
        assertTrue(emptyFresh.solverReached)
        assertTrue(emptyFresh.inputExtracted)
        assertEquals(CallsReplayStatus.REJECTED, emptyFresh.replayStatus)

        val frozenStop = results.single { result -> result.profile == CallsExperimentProfile.FROZEN_STOP }
        assertTrue(frozenStop.solverReached)
        assertTrue(frozenStop.inputExtracted)
        assertEquals(CallsReplayStatus.CONFIRMED, frozenStop.replayStatus)

        val emptyStop = results.single { result -> result.profile == CallsExperimentProfile.EMPTY_STOP }
        assertFalse(emptyStop.solverReached)
        assertFalse(emptyStop.inputExtracted)
        assertNull(emptyStop.replayStatus)
    }

    @Test
    fun `aggregator counts each profile from raw rows without collapsing outcome stages`(@TempDir directory: Path) {
        val rawOutput = directory.resolve("results.jsonl")
        val statuses = mapOf(
            CallsExperimentProfile.EMPTY_STOP to CallsSymbolicStatus.TIMEOUT,
            CallsExperimentProfile.EMPTY_FRESH to CallsSymbolicStatus.REACHED,
            CallsExperimentProfile.FROZEN_STOP to CallsSymbolicStatus.REACHED,
            CallsExperimentProfile.FROZEN_FRESH to CallsSymbolicStatus.TOOL_ERROR,
        )
        val engine = CallsSymbolicEngine { request ->
            val status = statuses.getValue(request.profile)
            result(
                status = status,
                inputs = if (status == CallsSymbolicStatus.REACHED) {
                    listOf(JsConcreteValue.Boolean(request.profile.usesFrozenModels))
                } else {
                    null
                },
            )
        }
        val replayer = CallsTargetReplayer { _, _, inputs, _, _ ->
            val input = inputs.single() as JsConcreteValue.Boolean
            CallsSourceReplayResult(
                status = if (input.value) CallsReplayStatus.CONFIRMED else CallsReplayStatus.REJECTED,
            )
        }

        CallsExperimentRunner(symbolicEngine = engine, targetReplayer = replayer).run(
            manifest = manifest(sourceRoot = ".", seeds = listOf(0L, 3L)),
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )
        val summary = CallsExperimentAggregator.summarize(rawOutput)

        assertEquals("fixture", summary.experimentId)
        assertEquals(1, summary.commonEligibleTargets)
        assertEquals(8, summary.resultRows)
        assertEquals(
            CallsProfileSummary(
                runs = 2,
                solverReached = 0,
                inputExtracted = 0,
                replayConfirmed = 0,
                replayRejected = 0,
                unsupported = 0,
                timeouts = 2,
                toolErrors = 0,
            ),
            summary.byProfile.getValue(CallsExperimentProfile.EMPTY_STOP),
        )
        assertEquals(2, summary.byProfile.getValue(CallsExperimentProfile.EMPTY_FRESH).replayRejected)
        assertEquals(2, summary.byProfile.getValue(CallsExperimentProfile.FROZEN_STOP).replayConfirmed)
        assertEquals(2, summary.byProfile.getValue(CallsExperimentProfile.FROZEN_FRESH).toolErrors)
    }

    private fun manifest(sourceRoot: String, seeds: List<Long>) = CallsExperimentManifest(
        schemaVersion = CallsExperimentManifest.SCHEMA_VERSION,
        experimentId = "fixture",
        toolRevision = "tool-revision",
        nativeFrontendRevision = "frontend-revision",
        solver = "Z3",
        searchPolicy = "BFS",
        modelSet = CallsModelSetIdentity(
            ids = setOf("ts.array.pop", "ts.array.shift"),
            catalogFingerprint = "frozen-fingerprint",
            sourceHash = "source-hash",
            etsIrHash = "ets-ir-hash",
            toolRevision = "tool-revision",
        ),
        seeds = seeds,
        perTargetBudgetMillis = 1_000L,
        projects = listOf(
            CallsProjectCase(
                projectId = "fixture/project",
                revision = "project-revision",
                sourceRoot = sourceRoot,
                development = true,
                functions = listOf(
                    CallsFunctionCase(
                        functionId = "fixture.ts::predicate/1",
                        module = "fixture.ts",
                        entryPoint = TypeScriptEntryPoint(
                            module = "fixture.ts",
                            exportName = "predicate",
                        ),
                        inputs = listOf(PropertyInput(name = "value", domain = BooleanDomain)),
                        targets = listOf(
                            CallsSourceTarget(
                                targetId = "fixture.ts::predicate/1#return",
                                siteId = "fixture.ts:1:1-1:12::predicate/1",
                                sourcePath = "fixture.ts",
                                sourceSha256 = "source-hash",
                                startOffset = 0,
                                endOffset = 11,
                                start = CallsSourcePosition(line = 0, column = 0),
                                end = CallsSourcePosition(line = 0, column = 11),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun result(
        status: CallsSymbolicStatus,
        inputs: List<JsConcreteValue>? = null,
    ) = CallsSymbolicSearchResult(
        status = status,
        inputs = inputs,
        catalogFingerprint = "runtime-fingerprint",
        elapsedMillis = 7L,
    )

    private fun readRecords(path: Path): List<CallsRawRecord> = Files.readAllLines(path)
        .filter(String::isNotBlank)
        .map { line -> CallsExperimentJson.json.decodeFromString<CallsRawRecord>(line) }
}
