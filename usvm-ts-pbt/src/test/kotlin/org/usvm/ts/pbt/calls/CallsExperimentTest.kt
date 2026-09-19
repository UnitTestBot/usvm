package org.usvm.ts.pbt.calls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

                CallsExperimentProfile.FROZEN_FRESH -> result(
                    status = CallsSymbolicStatus.UNREPRESENTABLE,
                    solverReached = true,
                )
            }
        }
        val replayer = CallsTargetReplayer { _, _, inputs, _, _ ->
            val input = inputs.single() as JsConcreteValue.Boolean

            CallsSourceReplayResult(
                status = if (input.value) CallsReplayStatus.CONFIRMED else CallsReplayStatus.REJECTED,
            )
        }
        val rawOutput = directory.resolve("raw/results.jsonl")

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = replayer,
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
        ).run(
            manifest = manifest(sourceRoot = ".", seeds = listOf(1L)),
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )

        val records = readRecords(rawOutput)
        val metadata = records.filterIsInstance<CallsRunMetadata>().single()
        val results = records.filterIsInstance<CallsTargetResult>()
        val completion = records.filterIsInstance<CallsRunCompletion>().single()

        assertEquals(1, metadata.commonEligibleTargets)
        assertEquals(4, completion.resultRows)
        assertFalse(Files.exists(rawOutput.resolveSibling("results.jsonl.partial")))
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
        assertEquals(listOf(JsConcreteValue.Boolean(false)), emptyFresh.inputs)
        assertEquals(CallsReplayStatus.REJECTED, emptyFresh.replayStatus)

        val frozenStop = results.single { result -> result.profile == CallsExperimentProfile.FROZEN_STOP }
        assertTrue(frozenStop.solverReached)
        assertTrue(frozenStop.inputExtracted)
        assertEquals(listOf(JsConcreteValue.Boolean(true)), frozenStop.inputs)
        assertEquals(CallsReplayStatus.CONFIRMED, frozenStop.replayStatus)

        val emptyStop = results.single { result -> result.profile == CallsExperimentProfile.EMPTY_STOP }
        assertFalse(emptyStop.solverReached)
        assertFalse(emptyStop.inputExtracted)
        assertNull(emptyStop.inputs)
        assertNull(emptyStop.replayStatus)

        val frozenFresh = results.single { result -> result.profile == CallsExperimentProfile.FROZEN_FRESH }
        assertTrue(frozenFresh.solverReached)
        assertFalse(frozenFresh.inputExtracted)
        assertNull(frozenFresh.inputs)
        assertNull(frozenFresh.replayStatus)
    }

    @Test
    fun `target result witness uses lossless concrete value serialization`() {
        val witness = listOf(
            JsConcreteValue.number(-0.0),
            JsConcreteValue.number(Double.NaN),
            JsConcreteValue.Array(
                elements = listOf(JsConcreteValue.Undefined, JsConcreteValue.Null, JsConcreteValue.String("value")),
            ),
        )
        val result = targetResult(inputs = witness)

        val encoded = CallsExperimentJson.json.encodeToString<CallsRawRecord>(result)
        val decoded = CallsExperimentJson.json.decodeFromString<CallsRawRecord>(encoded) as CallsTargetResult

        assertEquals(result, decoded)
        assertEquals(witness, decoded.inputs)
    }

    @Test
    fun `single witness replay uses selected stored inputs without symbolic search`(@TempDir directory: Path) {
        val rawOutput = directory.resolve("results.jsonl")
        val frozenManifest = manifest(sourceRoot = ".", seeds = listOf(11L))
        CallsExperimentRunner(
            symbolicEngine = CallsSymbolicEngine {
                result(
                    status = CallsSymbolicStatus.REACHED,
                    inputs = listOf(JsConcreteValue.Boolean(true)),
                )
            },
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ ->
                CallsSourceReplayResult(status = CallsReplayStatus.CONFIRMED)
            },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
        ).run(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )
        var replayedInputs: List<JsConcreteValue>? = null
        var replayedTarget: CallsSourceTarget? = null
        var replayedTimeout: Long? = null
        var verifiedCheckout: Path? = null
        val witnessReplayer = CallsWitnessReplayer(
            targetReplayer = CallsTargetReplayer { _, _, inputs, target, timeoutMillis ->
                replayedInputs = inputs
                replayedTarget = target
                replayedTimeout = timeoutMillis

                CallsSourceReplayResult(status = CallsReplayStatus.REJECTED)
            },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            verifyProjectCheckout = { checkout, expectedRevision ->
                assertEquals("project-revision", expectedRevision)
                verifiedCheckout = checkout
            },
        )

        val replay = witnessReplayer.replay(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawInput = rawOutput,
            selector = selector(seed = 11L, profile = CallsExperimentProfile.FROZEN_STOP),
        )

        assertEquals(CallsReplayStatus.REJECTED, replay.status)
        assertEquals(listOf(JsConcreteValue.Boolean(true)), replayedInputs)
        assertEquals(frozenManifest.projects.single().functions.single().targets.single(), replayedTarget)
        assertEquals(1_000L, replayedTimeout)
        assertEquals(directory.toRealPath(), verifiedCheckout)
    }

    @Test
    fun `historical extracted row without stored witness reports replay unavailable first`(@TempDir directory: Path) {
        val rawOutput = directory.resolve("results.jsonl")
        val frozenManifest = manifest(sourceRoot = ".", seeds = listOf(5L))
        CallsExperimentRunner(
            symbolicEngine = CallsSymbolicEngine {
                result(
                    status = CallsSymbolicStatus.REACHED,
                    inputs = listOf(JsConcreteValue.Boolean(true)),
                )
            },
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ ->
                CallsSourceReplayResult(status = CallsReplayStatus.CONFIRMED)
            },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
        ).run(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )
        val historicalRecords = readRecords(rawOutput).map { record ->
            when {
                record is CallsRunMetadata -> record.copy(nativeFrontendSha256 = null)
                record is CallsTargetResult && record.profile == CallsExperimentProfile.EMPTY_FRESH -> {
                    record.copy(inputs = null)
                }

                else -> record
            }
        }
        writeRecords(rawOutput, historicalRecords)
        val witnessReplayer = CallsWitnessReplayer(
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("Replay must not run") },
            runtimeToolRevision = "different-runtime-revision",
            verifyProjectCheckout = { _, _ -> error("Checkout verification must not run") },
        )

        val error = assertFailsWith<IllegalStateException> {
            witnessReplayer.replay(
                manifest = frozenManifest,
                manifestDirectory = directory,
                rawInput = rawOutput,
                selector = selector(seed = 5L, profile = CallsExperimentProfile.EMPTY_FRESH),
            )
        }

        assertTrue(error.message.orEmpty().contains("historical raw artifact"))
        assertTrue(error.message.orEmpty().contains("single-witness replay is unavailable"))

        val manifestPath = directory.resolve("historical-manifest.json")
        val manifestJson = CallsExperimentJson.json.encodeToString(frozenManifest)
        val historicalManifest = JsonObject(
            CallsExperimentJson.json.parseToJsonElement(manifestJson).jsonObject - "nativeFrontendSha256",
        )
        Files.writeString(manifestPath, historicalManifest.toString())

        val cliError = assertFailsWith<IllegalStateException> {
            replayWitness(
                listOf(
                    manifestPath.toString(),
                    rawOutput.toString(),
                    "fixture/project",
                    "fixture.ts::predicate/1",
                    "fixture.ts::predicate/1#return",
                    CallsExperimentProfile.EMPTY_FRESH.name,
                    "5",
                ),
            )
        }

        assertTrue(cliError.message.orEmpty().contains("historical raw artifact"))
        assertTrue(cliError.message.orEmpty().contains("single-witness replay is unavailable"))
    }

    @Test
    fun `aggregator rejects an interrupted raw prefix without completion`(@TempDir directory: Path) {
        val rawOutput = directory.resolve("results.jsonl")
        val engine = CallsSymbolicEngine { result(status = CallsSymbolicStatus.UNREACHED) }

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("Replay must not run") },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
        ).run(
            manifest = manifest(sourceRoot = ".", seeds = listOf(0L)),
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )
        val interrupted = directory.resolve("interrupted.jsonl")
        Files.write(interrupted, Files.readAllLines(rawOutput).dropLast(1))

        assertFailsWith<IllegalArgumentException> {
            CallsExperimentAggregator.summarize(interrupted)
        }
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

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = replayer,
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
        ).run(
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
                symbolicStatuses = CallsSymbolicStatus.entries.associateWith { status ->
                    if (status == CallsSymbolicStatus.TIMEOUT) 2 else 0
                },
                replayStatuses = CallsReplayStatus.entries.associateWith { 0 },
                replayNotRun = 2,
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
        toolRevision = FIXTURE_TOOL_REVISION,
        nativeFrontendRevision = "frontend-revision",
        nativeFrontendSha256 = "frontend-sha256",
        solver = "Z3",
        searchPolicy = "BFS",
        modelSet = CallsModelSetIdentity(
            ids = setOf("ts.array.pop", "ts.array.shift"),
            catalogFingerprint = "frozen-fingerprint",
            sourceHash = "source-hash",
            etsIrHash = "ets-ir-hash",
            toolRevision = FIXTURE_TOOL_REVISION,
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
                        sourceFile = "fixture.ts",
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
        solverReached: Boolean = status == CallsSymbolicStatus.REACHED,
        inputs: List<JsConcreteValue>? = null,
    ) = CallsSymbolicSearchResult(
        status = status,
        solverReached = solverReached,
        inputs = inputs,
        catalogFingerprint = "runtime-fingerprint",
        elapsedMillis = 7L,
    )

    private fun readRecords(path: Path): List<CallsRawRecord> = Files.readAllLines(path)
        .filter(String::isNotBlank)
        .map { line -> CallsExperimentJson.json.decodeFromString<CallsRawRecord>(line) }

    private fun writeRecords(path: Path, records: List<CallsRawRecord>) {
        Files.writeString(
            path,
            records.joinToString(separator = "\n", postfix = "\n") { record ->
                CallsExperimentJson.json.encodeToString<CallsRawRecord>(record)
            },
        )
    }

    private fun selector(seed: Long, profile: CallsExperimentProfile) = CallsWitnessSelector(
        projectId = "fixture/project",
        functionId = "fixture.ts::predicate/1",
        targetId = "fixture.ts::predicate/1#return",
        profile = profile,
        seed = seed,
    )

    private fun targetResult(inputs: List<JsConcreteValue>) = CallsTargetResult(
        experimentId = "fixture",
        projectId = "fixture/project",
        revision = "project-revision",
        development = true,
        functionId = "fixture.ts::predicate/1",
        targetId = "fixture.ts::predicate/1#return",
        siteId = "fixture.ts:1:1-1:12::predicate/1",
        profile = CallsExperimentProfile.FROZEN_STOP,
        seed = 1L,
        symbolicStatus = CallsSymbolicStatus.REACHED,
        solverReached = true,
        inputExtracted = true,
        inputs = inputs,
        replayStatus = CallsReplayStatus.CONFIRMED,
        catalogFingerprint = "runtime-fingerprint",
        symbolicElapsedMillis = 7L,
    )

    private companion object {
        const val FIXTURE_TOOL_REVISION = "0000000000000000000000000000000000000001"
    }
}
