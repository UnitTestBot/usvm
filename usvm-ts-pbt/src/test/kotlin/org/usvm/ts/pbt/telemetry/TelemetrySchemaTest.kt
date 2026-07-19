package org.usvm.ts.pbt.telemetry

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.usvm.ts.pbt.report.ConfigEcho
import org.usvm.ts.pbt.report.HybridReport

class TelemetrySchemaTest {
    @ParameterizedTest
    @EnumSource(TelemetryReasonCode::class)
    fun `fake-clock fixture is deterministic for every terminal outcome`(reason: TelemetryReasonCode) {
        val clock = FakeClock(1_000)
        val recorder = TelemetryRecorder(clock)
        val target = recorder.target(
            methodId = "fixture.ts::C.f/1",
            branchId = "fixture.ts::C.f/1#if:7->8",
            capabilityLabels = listOf("supported", "primitive"),
        )

        clock.advance(1)
        target.machineStarted(counters(states = 0, steps = 0, queries = 0))
        when (reason) {
            TelemetryReasonCode.TIMEOUT_NO_PROGRESS -> {
                Unit
            }

            TelemetryReasonCode.GLOBAL_SAFETY_TIMEOUT,
            TelemetryReasonCode.UNREACHABLE_PRUNED,
            -> {
                clock.advance(1)
                target.terminalProgress(counters(activeRoots = 0, states = 1, steps = 2, queries = 1))
            }

            TelemetryReasonCode.SOLVER_REACHED -> {
                reachTarget(clock, target)
            }

            TelemetryReasonCode.MODEL_EXTRACTION_FAILED -> {
                reachTarget(clock, target)
                finishExtraction(clock, target)
            }

            TelemetryReasonCode.REPLAY_UNSUPPORTED,
            TelemetryReasonCode.REPLAY_DIVERGED,
            TelemetryReasonCode.REPLAY_WRONG_EDGE,
            TelemetryReasonCode.CONFIRMED,
            -> {
                reachTarget(clock, target)
                finishExtraction(clock, target)
                finishReplay(clock, target)
            }
        }

        clock.advance(1)
        val divergence = when (reason) {
            TelemetryReasonCode.REPLAY_UNSUPPORTED -> DivergenceObservation.first(call = "Symbol.iterator")
            TelemetryReasonCode.REPLAY_DIVERGED -> DivergenceObservation.first(stmt = "stmt:17", call = "Map.get")
            TelemetryReasonCode.REPLAY_WRONG_EDGE -> DivergenceObservation.notObservable()
            else -> null
        }
        val outcome = target.finish(reason, divergence = divergence)
        val telemetry = recorder.complete()

        assertEquals(1, outcome.timestamps!!.machineStartedAtMs)
        assertEquals(clock.current - 1_000, outcome.timestamps.terminalAtMs)
        assertEquals(reason, outcome.reason)
        assertEquals(1, telemetry.terminalCount)
        assertEquals(0, telemetry.notStartedCount)
        assertEquals(1, telemetry.expectedTargetCount)
        assertTrue(telemetry.complete)
        assertEquals(listOf("primitive", "supported"), outcome.capabilityLabels)
        assertEquals("shard-0", outcome.counters!!.shardId)
        assertTrue(checkNotNull(outcome.counters.solverQueries) >= 0)

        val encoded = HybridReport.encode(report(telemetry))
        assertTrue(encoded.contains("\"reason\": \"${reason.name.lowercase()}\""))
        assertTrue(encoded.contains("\"schemaVersion\": 1"))
        assertTrue(encoded.contains("\"startupFrontendMs\": 0"))
        assertEquals(report(telemetry), HybridReport.decode(encoded))
    }

    @Test
    fun `stage timings and explicit not-started target use only fake clock`() {
        val clock = FakeClock(50)
        val recorder = TelemetryRecorder(clock)

        recorder.startStage(TelemetryStage.STARTUP_FRONTEND)
        clock.advance(7)
        assertEquals(7, recorder.finishStage(TelemetryStage.STARTUP_FRONTEND))
        recorder.startStage(TelemetryStage.GENERATION)
        clock.advance(11)
        recorder.finishStage(TelemetryStage.GENERATION)
        recorder.startStage(TelemetryStage.SYMBOLIC)
        clock.advance(13)
        recorder.finishStage(TelemetryStage.SYMBOLIC)
        recorder.startStage(TelemetryStage.REPLAY)
        clock.advance(17)
        recorder.finishStage(TelemetryStage.REPLAY)

        recorder.target("method", "branch", listOf("needs_dynamic_probe")).notStarted()
        val telemetry = recorder.complete()

        assertEquals(StageDurations(7, 11, 13, 17), telemetry.stageDurations)
        assertEquals(48, telemetry.stageDurations.totalMs)
        assertEquals(0, telemetry.terminalCount)
        assertEquals(1, telemetry.notStartedCount)
        assertEquals(TargetTelemetryStatus.NOT_STARTED, telemetry.targets.single().status)
        assertNull(telemetry.targets.single().reason)
    }

    @Test
    fun `complete telemetry rejects a missing outcome while partial snapshot preserves denominator`() {
        val clock = FakeClock()
        val recorder = TelemetryRecorder(clock)
        recorder.target("method", "unfinished")
        recorder.startStage(TelemetryStage.SYMBOLIC)
        clock.advance(9)

        val partial = recorder.snapshot()
        assertFalse(partial.complete)
        assertEquals(1, partial.expectedTargetCount)
        assertTrue(partial.targets.isEmpty())
        assertEquals(9, partial.stageDurations.symbolicMs)
        recorder.finishStage(TelemetryStage.SYMBOLIC)
        assertThrows(IllegalStateException::class.java) { recorder.complete() }
    }

    @Test
    fun `terminal and not-started states are exclusive`() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetTelemetry(
                methodId = "method",
                branchId = "branch",
                status = TargetTelemetryStatus.NOT_STARTED,
                reason = TelemetryReasonCode.CONFIRMED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            HybridTelemetry(complete = true, expectedTargetCount = 1)
        }
    }

    @Test
    fun `every replay failure requires explicit divergence observability`() {
        val clock = FakeClock()
        val recorder = TelemetryRecorder(clock)
        val target = recorder.target("method", "branch")
        target.machineStarted(counters())
        reachTarget(clock, target)
        finishExtraction(clock, target)
        finishReplay(clock, target)

        assertThrows(IllegalArgumentException::class.java) {
            target.finish(TelemetryReasonCode.REPLAY_DIVERGED)
        }
    }

    @Test
    fun `unknown reason code is rejected during JSON decode`() {
        val clock = FakeClock()
        val recorder = TelemetryRecorder(clock)
        val target = recorder.target("method", "branch")
        target.machineStarted(counters())
        reachTarget(clock, target)
        finishExtraction(clock, target)
        finishReplay(clock, target)
        target.finish(TelemetryReasonCode.CONFIRMED)
        val encoded = HybridReport.encode(report(recorder.complete()))
        val withUnknownReason = encoded.replace("\"confirmed\"", "\"future_unknown_reason\"")

        assertThrows(SerializationException::class.java) {
            HybridReport.decode(withUnknownReason)
        }
    }

    @Test
    fun `legacy report decodes and flag-off encoding keeps telemetry absent`() {
        val legacy = """
            {
              "config": {
                "mode": "HYBRID",
                "seed": 42,
                "pbtMaxIterations": 100,
                "pbtTimeBudgetMs": 1000,
                "perTargetTimeoutMs": 50,
                "hintFallback": true
              },
              "methods": []
            }
        """.trimIndent()

        val decoded = HybridReport.decode(legacy)
        assertNull(decoded.telemetry)
        val reencoded = HybridReport.encode(decoded)
        assertFalse(reencoded.contains("\"telemetry\""))
        assertEquals(decoded, HybridReport.decode(reencoded))
    }

    @Test
    fun `clock regression is rejected without sleeping`() {
        val clock = FakeClock(10)
        val recorder = TelemetryRecorder(clock)
        val target = recorder.target("method", "branch")
        clock.current = 9

        assertThrows(IllegalStateException::class.java) {
            target.machineStarted(counters())
        }
    }

    private fun reachTarget(clock: FakeClock, target: TargetTelemetryRecorder) {
        clock.advance(1)
        target.targetReached(counters(activeRoots = 1, states = 2, steps = 3, queries = 1))
    }

    private fun finishExtraction(clock: FakeClock, target: TargetTelemetryRecorder) {
        clock.advance(1)
        target.modelExtractionFinished(counters(activeRoots = 1, states = 3, steps = 5, queries = 2))
    }

    private fun finishReplay(clock: FakeClock, target: TargetTelemetryRecorder) {
        clock.advance(1)
        target.replayFinished(counters(activeRoots = 0, states = 4, steps = 8, queries = 3))
    }

    private fun counters(
        activeRoots: Int = 1,
        states: Long = 0,
        steps: Long = 0,
        queries: Long? = 0,
    ): TargetCounters = TargetCounters(
        activeRoots = activeRoots,
        shardId = "shard-0",
        states = states,
        steps = steps,
        solverQueries = queries,
    )

    private fun report(telemetry: HybridTelemetry?): HybridReport = HybridReport(
        config = ConfigEcho(
            mode = "HYBRID",
            seed = 42,
            pbtMaxIterations = 100,
            pbtTimeBudgetMs = 1_000,
            perTargetTimeoutMs = 50,
            hintFallback = true,
        ),
        methods = emptyList(),
        telemetry = telemetry,
    )

    private class FakeClock(var current: Long = 0) : TelemetryClock {
        override fun nowMs(): Long = current

        fun advance(milliseconds: Long) {
            current += milliseconds
        }
    }
}
