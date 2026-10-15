package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.calls.CallsExperimentTestSupport.FIXTURE_TOOL_REVISION
import org.usvm.ts.calls.CallsExperimentTestSupport.manifest
import org.usvm.ts.calls.CallsExperimentTestSupport.readRecords
import org.usvm.ts.calls.CallsExperimentTestSupport.result
import org.usvm.ts.calls.CallsExperimentTestSupport.targetResult
import org.usvm.ts.calls.CallsExperimentTestSupport.writeRecords
import org.usvm.ts.pbt.model.JsConcreteValue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CallsExperimentSummaryTest {
    @Test
    fun `aggregator rejects an interrupted raw prefix without completion`(@TempDir directory: Path) {
        val rawOutput = directory.resolve("results.jsonl")
        val engine = CallsSymbolicEngine { result(status = CallsSymbolicStatus.UNREACHED) }

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("Replay must not run") },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            sourceCheckoutVerifier = { _, _ -> },
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
    fun `summarize rejects its raw input as output without changing it`(@TempDir directory: Path) {
        val rawInput = directory.resolve("results.jsonl")
        writeCompletedRawFixture(rawInput)
        val originalBytes = Files.readAllBytes(rawInput)

        val failure = assertFailsWith<IllegalArgumentException> {
            main(arrayOf("summarize", rawInput.toString(), rawInput.toString()))
        }

        assertEquals("Summary output must differ from raw input", failure.message)
        assertContentEquals(originalBytes, Files.readAllBytes(rawInput))
    }

    @Test
    fun `summarize rejects a link to its raw input without changing it`(@TempDir directory: Path) {
        val rawInput = directory.resolve("results.jsonl")
        writeCompletedRawFixture(rawInput)
        val alias = directory.resolve("raw-alias.jsonl")
        val linkCreated = try {
            Files.createSymbolicLink(alias, rawInput)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }

        assumeTrue(linkCreated, "Symbolic links are unavailable")
        val originalBytes = Files.readAllBytes(rawInput)

        val failure = assertFailsWith<IllegalArgumentException> {
            main(arrayOf("summarize", rawInput.toString(), alias.toString()))
        }

        assertEquals("Summary output must differ from raw input", failure.message)
        assertContentEquals(originalBytes, Files.readAllBytes(rawInput))
    }

    @Test
    fun `summarize replaces a distinct existing summary`(@TempDir directory: Path) {
        val rawInput = directory.resolve("results.jsonl")
        writeCompletedRawFixture(rawInput)
        val originalBytes = Files.readAllBytes(rawInput)
        val output = directory.resolve("summary.json")
        Files.writeString(output, "stale summary")

        main(arrayOf("summarize", rawInput.toString(), output.toString()))

        val summary = CallsExperimentJson.json.decodeFromString<CallsExperimentSummary>(Files.readString(output))
        assertEquals("fixture", summary.experimentId)
        assertEquals(4, summary.resultRows)
        assertContentEquals(originalBytes, Files.readAllBytes(rawInput))
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
            sourceCheckoutVerifier = { _, _ -> },
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

    @Test
    fun `solver unknown survives raw serialization and summary without becoming unreached`(@TempDir directory: Path) {
        val rawOutput = directory.resolve("results.jsonl")
        val diagnostic = "Solver returned UNKNOWN while checking initial constraints"
        val engine = CallsSymbolicEngine {
            CallsSymbolicSearchResult(
                status = CallsSymbolicStatus.UNKNOWN,
                elapsedMillis = 7L,
                diagnostic = diagnostic,
            )
        }

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("No witness to replay") },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            sourceCheckoutVerifier = { _, _ -> },
        ).run(
            manifest = manifest(sourceRoot = ".", seeds = listOf(0L)),
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )
        val records = readRecords(rawOutput).filterIsInstance<CallsTargetResult>()
        val summary = CallsExperimentAggregator.summarize(rawOutput)

        assertEquals(4, records.size)
        records.forEach { record ->
            assertEquals(CallsSymbolicStatus.UNKNOWN, record.symbolicStatus)
            assertFalse(record.solverReached)
            assertFalse(record.inputExtracted)
            assertNull(record.replayStatus)
            assertEquals(diagnostic, record.diagnostic)
        }
        summary.byProfile.values.forEach { profile ->
            assertEquals(1, profile.symbolicStatuses.getValue(CallsSymbolicStatus.UNKNOWN))
            assertEquals(0, profile.symbolicStatuses.getValue(CallsSymbolicStatus.UNREACHED))
            assertEquals(0, profile.timeouts)
            assertEquals(0, profile.toolErrors)
        }
    }

    private fun writeCompletedRawFixture(path: Path) {
        val row = targetResult(inputs = listOf(JsConcreteValue.Boolean(value = true)))
        val target = CallsRunTargetIdentity(
            projectId = row.projectId,
            revision = row.revision,
            development = row.development,
            functionId = row.functionId,
            targetId = row.targetId,
            siteId = row.siteId,
            targetMode = row.targetMode,
        )
        val metadata = CallsRunMetadata(
            experimentId = row.experimentId,
            toolRevision = FIXTURE_TOOL_REVISION,
            nativeFrontendRevision = "bundled:test",
            modelSet = CallsModelSetIdentity(ids = emptySet(), toolRevision = FIXTURE_TOOL_REVISION),
            profiles = CallsExperimentProfile.entries,
            seeds = listOf(row.seed),
            commonEligibleTargets = 1,
            targets = listOf(target),
        )
        val completion = CallsRunCompletion(
            experimentId = row.experimentId,
            resultRows = CallsExperimentProfile.entries.size,
        )
        val records: List<CallsRawRecord> = buildList {
            add(metadata)
            CallsExperimentProfile.entries.forEach { profile -> add(row.copy(profile = profile)) }
            add(completion)
        }

        writeRecords(path, records)
    }
}
