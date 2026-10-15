package org.usvm.ts.calls

import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.ts.calls.CallsExperimentTestSupport.FIXTURE_TOOL_REVISION
import org.usvm.ts.calls.CallsExperimentTestSupport.manifest
import org.usvm.ts.calls.CallsExperimentTestSupport.readRecords
import org.usvm.ts.calls.CallsExperimentTestSupport.result
import org.usvm.ts.pbt.model.JsConcreteValue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallsExperimentTest {
    @Test
    fun `runner rejects changed external frontend bytes before raw publication`(@TempDir directory: Path) {
        val sourceManifest = frozenGitManifest(directory)
        val frontend = Files.createDirectory(directory.resolve("frontend"))
        Files.writeString(frontend.resolve(".gitignore"), "dist/\n")
        runCallsGit(frontend, "init")
        runCallsGit(frontend, "config", "user.name", "USVM Tests")
        runCallsGit(frontend, "config", "user.email", "usvm@example.test")
        runCallsGit(frontend, "add", ".")
        runCallsGit(frontend, "commit", "-m", "Create frontend fixture")
        val dist = Files.createDirectory(frontend.resolve("dist"))
        val script = dist.resolve("index.js")
        Files.writeString(script, "original script")
        Files.writeString(dist.resolve("lib.d.ts"), "original library")
        val frontendIdentity = callsExternalFrontendIdentity(frontend)
        val manifest = sourceManifest.copy(nativeFrontendRevision = frontendIdentity)
        val rawOutput = directory.resolve("results.jsonl")
        val engine = CallsSymbolicEngine {
            Files.writeString(script, "changed script")
            result(status = CallsSymbolicStatus.UNREACHED)
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            CallsExperimentRunner(
                symbolicEngine = engine,
                targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("Replay must not run") },
                runtimeToolRevision = FIXTURE_TOOL_REVISION,
                frontendRuntimeVerifier = { identity -> verifyCallsExternalFrontendRuntime(frontend, identity) },
            ).run(
                manifest = manifest,
                manifestDirectory = directory,
                rawOutput = rawOutput,
            )
        }

        assertTrue(failure.message.orEmpty().contains("runtime digest differs"))
        assertFalse(Files.exists(rawOutput))
    }

    @Test
    fun `runner rejects a source edit during symbolic search before replay or publication`(@TempDir directory: Path) {
        val frozenManifest = frozenGitManifest(directory)
        val sourceFile = directory.resolve("source/fixture.ts")
        val rawOutput = directory.resolve("results.jsonl")
        val engine = CallsSymbolicEngine {
            Files.writeString(sourceFile, "export function predicate(value: boolean): boolean { return !value; }")
            result(status = CallsSymbolicStatus.UNREACHED)
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            CallsExperimentRunner(
                symbolicEngine = engine,
                targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("Replay must not run") },
                runtimeToolRevision = FIXTURE_TOOL_REVISION,
            ).run(
                manifest = frozenManifest,
                manifestDirectory = directory,
                rawOutput = rawOutput,
            )
        }

        assertTrue(failure.message.orEmpty().contains("Git diff --quiet HEAD -- failed"))
        assertFalse(Files.exists(rawOutput))
    }

    @Test
    fun `runner rejects a source edit during replay before publication`(@TempDir directory: Path) {
        val frozenManifest = frozenGitManifest(directory)
        val sourceFile = directory.resolve("source/fixture.ts")
        val rawOutput = directory.resolve("results.jsonl")
        val engine = CallsSymbolicEngine {
            result(
                status = CallsSymbolicStatus.REACHED,
                inputs = listOf(JsConcreteValue.Boolean(value = true)),
            )
        }
        val replayer = CallsTargetReplayer { _, _, _, _, _ ->
            Files.writeString(sourceFile, "export function predicate(value: boolean): boolean { return !value; }")
            CallsSourceReplayResult(status = CallsReplayStatus.CONFIRMED)
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            CallsExperimentRunner(
                symbolicEngine = engine,
                targetReplayer = replayer,
                runtimeToolRevision = FIXTURE_TOOL_REVISION,
            ).run(
                manifest = frozenManifest,
                manifestDirectory = directory,
                rawOutput = rawOutput,
            )
        }

        assertTrue(failure.message.orEmpty().contains("Git diff --quiet HEAD -- failed"))
        assertFalse(Files.exists(rawOutput))
    }

    @Test
    fun `runner forwards an explicitly configured solver`(@TempDir directory: Path) {
        val solvers = mutableListOf<SolverType>()
        val engine = CallsSymbolicEngine { request ->
            solvers += request.solverType
            result(status = CallsSymbolicStatus.UNREACHED)
        }
        val z3Manifest = manifest(sourceRoot = ".", seeds = listOf(17L)).copy(solver = "Z3")

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("No witness to replay") },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            sourceCheckoutVerifier = { _, _ -> },
        ).run(
            manifest = z3Manifest,
            manifestDirectory = directory,
            rawOutput = directory.resolve("results.jsonl"),
        )

        assertEquals(setOf(SolverType.Z3), solvers.toSet())
    }

    @Test
    fun `runner persists call events before search returns without counting them as results`(@TempDir directory: Path) {
        val source = Path.of(checkNotNull(javaClass.getResource("/calls/SourceTargetReplayFixture.ts")).toURI())
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val method = file.allClasses.flatMap { cls -> cls.methods }
            .single { candidate -> candidate.name == "completesReturnExpression" }
        val callSite = method.cfg.stmts.first { statement -> statement.location.origin != null }
        val rawOutput = directory.resolve("results.jsonl")
        val partialOutput = directory.resolve("results.jsonl.partial")
        val event = TsUnknownCallEvent(
            callSite = callSite,
            callee = method.signature,
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
            decision = TsUnknownCallDecision.ResidualFallback(policy = TsResidualCallPolicy.STOP_PATH),
        )
        val engine = CallsSymbolicEngine { request ->
            checkNotNull(request.unknownCallEventSink).invoke(event)
            val appended = readRecords(partialOutput).last() as CallsUnknownCallRecord
            assertEquals(request.profile, appended.cell.profile)
            assertEquals(1, appended.eventIndex)

            result(status = CallsSymbolicStatus.UNREACHED)
        }

        CallsExperimentRunner(
            symbolicEngine = engine,
            targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("No witness to replay") },
            runtimeToolRevision = FIXTURE_TOOL_REVISION,
            sourceCheckoutVerifier = { _, _ -> },
        ).run(
            manifest = manifest(sourceRoot = ".", seeds = listOf(17L)),
            manifestDirectory = directory,
            rawOutput = rawOutput,
        )

        val records = readRecords(rawOutput)
        assertEquals(4, records.filterIsInstance<CallsUnknownCallRecord>().size)
        assertEquals(4, CallsExperimentAggregator.summarize(rawOutput).resultRows)
    }

    @Test
    fun `runner does not publish completion after an observer write failure`(@TempDir directory: Path) {
        val source = Path.of(checkNotNull(javaClass.getResource("/calls/SourceTargetReplayFixture.ts")).toURI())
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val method = file.allClasses.flatMap { cls -> cls.methods }
            .single { candidate -> candidate.name == "completesReturnExpression" }
        val callSite = method.cfg.stmts.first { statement -> statement.location.origin != null }
        val event = TsUnknownCallEvent(
            callSite = callSite,
            callee = method.signature,
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
            decision = TsUnknownCallDecision.ResidualFallback(policy = TsResidualCallPolicy.STOP_PATH),
        )
        val engine = CallsSymbolicEngine { request ->
            runCatching { checkNotNull(request.unknownCallEventSink).invoke(event) }
            result(status = CallsSymbolicStatus.UNREACHED)
        }
        val rawOutput = directory.resolve("results.jsonl")

        val failure = assertFailsWith<IllegalStateException> {
            CallsExperimentRunner(
                symbolicEngine = engine,
                targetReplayer = CallsTargetReplayer { _, _, _, _, _ -> error("No witness to replay") },
                runtimeToolRevision = FIXTURE_TOOL_REVISION,
                eventRecordWriter = { _, _ -> error("Event write failed") },
                sourceCheckoutVerifier = { _, _ -> },
            ).run(
                manifest = manifest(sourceRoot = ".", seeds = listOf(17L)),
                manifestDirectory = directory,
                rawOutput = rawOutput,
            )
        }

        assertTrue(failure.message.orEmpty().contains("Calls event"))
        assertFalse(Files.exists(rawOutput))
    }

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
            sourceCheckoutVerifier = { _, _ -> },
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

    private fun frozenGitManifest(directory: Path): CallsExperimentManifest {
        val sourceRoot = Files.createDirectory(directory.resolve("source"))
        Files.writeString(
            sourceRoot.resolve("fixture.ts"),
            "export function predicate(value: boolean): boolean { return value; }",
        )
        runCallsGit(sourceRoot, "init")
        runCallsGit(sourceRoot, "config", "user.name", "USVM Tests")
        runCallsGit(sourceRoot, "config", "user.email", "usvm@example.test")
        runCallsGit(sourceRoot, "add", ".")
        runCallsGit(sourceRoot, "commit", "-m", "Create fixture")
        val revision = runCallsGit(sourceRoot, "rev-parse", "HEAD").trim()
        val base = manifest(sourceRoot = "source", seeds = listOf(0L))

        return base.copy(projects = listOf(base.projects.single().copy(revision = revision)))
    }
}
