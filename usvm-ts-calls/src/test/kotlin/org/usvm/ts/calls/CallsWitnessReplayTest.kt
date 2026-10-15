package org.usvm.ts.calls

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.calls.CallsExperimentTestSupport.FIXTURE_TOOL_REVISION
import org.usvm.ts.calls.CallsExperimentTestSupport.manifest
import org.usvm.ts.calls.CallsExperimentTestSupport.readRecords
import org.usvm.ts.calls.CallsExperimentTestSupport.result
import org.usvm.ts.calls.CallsExperimentTestSupport.writeRecords
import org.usvm.ts.pbt.model.JsConcreteValue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CallsWitnessReplayTest {
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
            sourceCheckoutVerifier = { _, _ -> },
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

        val expectedTarget = frozenManifest.projects
            .single()
            .functions
            .single()
            .targets
            .single()
        assertEquals(CallsReplayStatus.REJECTED, replay.status)
        assertEquals(listOf(JsConcreteValue.Boolean(true)), replayedInputs)
        assertEquals(expectedTarget, replayedTarget)
        assertEquals(1_000L, replayedTimeout)
        assertEquals(directory.toRealPath(), verifiedCheckout)
    }

    @Test
    fun `single witness replay accepts stored completed return target`(@TempDir directory: Path) {
        val baseManifest = manifest(sourceRoot = ".", seeds = listOf(13L))
        val project = baseManifest.projects.single()
        val function = project.functions.single()
        val completedReturn = function.targets.single().copy(
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpressionStartOffset = 7,
            returnExpressionEndOffset = 10,
        )
        val frozenManifest = baseManifest.copy(
            projects = listOf(project.copy(functions = listOf(function.copy(targets = listOf(completedReturn))))),
        )
        val rawOutput = directory.resolve("results.jsonl")
        val storedInputs = listOf(JsConcreteValue.Boolean(true))
        CallsExperimentRunner(
            symbolicEngine = CallsSymbolicEngine {
                result(status = CallsSymbolicStatus.REACHED, inputs = storedInputs)
            },
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ ->
                CallsSourceReplayResult(status = CallsReplayStatus.CONFIRMED)
            },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            sourceCheckoutVerifier = { _, _ -> },
        ).run(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )

        val raw = CallsRawResultsReader.read(rawOutput)
        val storedRow = raw.results.single { row -> row.profile == CallsExperimentProfile.FROZEN_STOP }
        var replayedInputs: List<JsConcreteValue>? = null
        var replayedTarget: CallsSourceTarget? = null
        val replay = CallsWitnessReplayer(
            targetReplayer = CallsTargetReplayer { _, _, inputs, target, _ ->
                replayedInputs = inputs
                replayedTarget = target
                CallsSourceReplayResult(status = CallsReplayStatus.REJECTED)
            },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            verifyProjectCheckout = { _, _ -> },
        ).replay(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawInput = rawOutput,
            selector = selector(seed = 13L, profile = CallsExperimentProfile.FROZEN_STOP),
        )

        assertEquals(CallsSourceTargetMode.COMPLETED_RETURN, raw.metadata.targets.single().targetMode)
        assertEquals(CallsSourceTargetMode.COMPLETED_RETURN, storedRow.targetMode)
        assertEquals(storedInputs, storedRow.inputs)
        assertEquals(storedInputs, replayedInputs)
        assertEquals(completedReturn, replayedTarget)
        assertEquals(CallsReplayStatus.REJECTED, replay.status)
    }

    @Test
    fun `single witness replay selects function when projects share an ID`(@TempDir directory: Path) {
        val baseManifest = manifest(sourceRoot = ".", seeds = listOf(19L))
        val firstProject = baseManifest.projects.single()
        val firstFunction = firstProject.functions.single()
        val secondTarget = firstFunction.targets.single().copy(
            targetId = "fixture.ts::other/1#return",
            siteId = "fixture.ts:2:1-2:12::other/1",
        )
        val secondFunction = firstFunction.copy(
            functionId = "fixture.ts::other/1",
            targets = listOf(secondTarget),
        )
        val secondProject = firstProject.copy(
            revision = "second-project-revision",
            functions = listOf(secondFunction),
        )
        val frozenManifest = baseManifest.copy(projects = listOf(firstProject, secondProject))
        val rawOutput = directory.resolve("results.jsonl")
        val selectedWitness = CallsWitnessSelector(
            projectId = secondProject.projectId,
            functionId = secondFunction.functionId,
            targetId = secondTarget.targetId,
            profile = CallsExperimentProfile.FROZEN_STOP,
            seed = 19L,
        )

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
            sourceCheckoutVerifier = { _, _ -> },
        ).run(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )

        var verifiedRevision: String? = null
        var replayedTarget: CallsSourceTarget? = null
        val replay = CallsWitnessReplayer(
            targetReplayer = CallsTargetReplayer { _, _, _, target, _ ->
                replayedTarget = target
                CallsSourceReplayResult(status = CallsReplayStatus.CONFIRMED)
            },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            verifyProjectCheckout = { _, revision -> verifiedRevision = revision },
        ).replay(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawInput = rawOutput,
            selector = selectedWitness,
        )

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status)
        assertEquals("second-project-revision", verifiedRevision)
        assertEquals(secondTarget, replayedTarget)
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
            sourceCheckoutVerifier = { _, _ -> },
        ).run(
            manifest = frozenManifest,
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )
        val historicalRecords = readRecords(rawOutput).map { record ->
            when {
                record is CallsTargetResult && record.profile == CallsExperimentProfile.EMPTY_FRESH -> {
                    record.copy(inputs = null)
                }

                else -> {
                    record
                }
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
        Files.writeString(manifestPath, CallsExperimentJson.encodeManifest(frozenManifest))

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

    private fun selector(seed: Long, profile: CallsExperimentProfile) = CallsWitnessSelector(
        projectId = "fixture/project",
        functionId = "fixture.ts::predicate/1",
        targetId = "fixture.ts::predicate/1#return",
        profile = profile,
        seed = seed,
    )
}
