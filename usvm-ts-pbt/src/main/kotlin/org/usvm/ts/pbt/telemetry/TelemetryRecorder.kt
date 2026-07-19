package org.usvm.ts.pbt.telemetry

/** Injectable monotonic clock returning milliseconds in an arbitrary epoch. */
fun interface TelemetryClock {
    fun nowMs(): Long
}

/** A monotonic clock suitable for production telemetry without wall-clock jumps. */
class SystemTelemetryClock : TelemetryClock {
    private val originNanos = System.nanoTime()

    override fun nowMs(): Long = (System.nanoTime() - originNanos) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND: Long = 1_000_000
    }
}

enum class TelemetryStage {
    STARTUP_FRONTEND,
    GENERATION,
    SYMBOLIC,
    REPLAY,
}

/**
 * Integration API for future producer hooks. It deliberately has no dependency
 * on HybridAnalyzer, SymbolicPhase, or TsMachine so those owners can attach it
 * behind a feature flag without changing the artifact contract.
 *
 * This recorder is intentionally single-threaded.
 */
class TelemetryRecorder(
    private val clock: TelemetryClock = SystemTelemetryClock(),
) {
    private data class TargetIdentity(val methodId: String, val branchId: String)

    private val clockOriginMs = clock.nowMs()
    private var lastClockValueMs = clockOriginMs
    private val targets = linkedMapOf<TargetIdentity, TargetTelemetryRecorder>()
    private val stageDurations = TelemetryStage.entries.associateWithTo(linkedMapOf()) { 0L }
    private var activeStage: TelemetryStage? = null
    private var activeStageStartedAtMs: Long? = null
    private var closed = false

    fun target(
        methodId: String,
        branchId: String,
        capabilityLabels: List<String> = emptyList(),
    ): TargetTelemetryRecorder {
        checkOpen()
        val identity = TargetIdentity(methodId, branchId)
        require(identity !in targets) { "target telemetry is already registered for $methodId/$branchId" }
        return TargetTelemetryRecorder(this, methodId, branchId, capabilityLabels).also {
            targets[identity] = it
        }
    }

    fun startStage(stage: TelemetryStage) {
        checkOpen()
        check(activeStage == null) { "telemetry stage $activeStage is already running" }
        activeStage = stage
        activeStageStartedAtMs = nowRelativeMs()
    }

    fun finishStage(stage: TelemetryStage): Long {
        checkOpen()
        check(activeStage == stage) { "cannot finish $stage while $activeStage is running" }
        val elapsed = nowRelativeMs() - checkNotNull(activeStageStartedAtMs)
        stageDurations[stage] = checkNotNull(stageDurations[stage]) + elapsed
        activeStage = null
        activeStageStartedAtMs = null
        return elapsed
    }

    /** A partial flush keeps unfinished registered targets visible in expectedTargetCount. */
    fun snapshot(): HybridTelemetry = build(complete = false)

    /** Closes the recorder after checking that the target denominator is complete. */
    fun complete(): HybridTelemetry {
        val telemetry = build(complete = true)
        closed = true
        return telemetry
    }

    internal fun nowRelativeMs(): Long {
        val value = clock.nowMs()
        check(value >= lastClockValueMs) {
            "telemetry clock regressed from $lastClockValueMs to $value"
        }
        lastClockValueMs = value
        return value - clockOriginMs
    }

    private fun build(complete: Boolean): HybridTelemetry {
        checkOpen()
        if (complete) {
            check(activeStage == null) { "cannot complete telemetry while stage $activeStage is running" }
        }
        val durations = stageDurations.toMutableMap()
        val runningStage = activeStage
        if (runningStage != null) {
            val runningElapsed = nowRelativeMs() - checkNotNull(activeStageStartedAtMs)
            durations[runningStage] = checkNotNull(durations[runningStage]) + runningElapsed
        }
        val finishedTargets = targets.values.mapNotNull(TargetTelemetryRecorder::result)
            .sortedWith(compareBy(TargetTelemetry::methodId, TargetTelemetry::branchId))
        if (complete) {
            check(finishedTargets.size == targets.size) {
                "${targets.size - finishedTargets.size} targets have neither a terminal outcome nor not_started"
            }
        }
        return HybridTelemetry(
            complete = complete,
            expectedTargetCount = targets.size,
            stageDurations = StageDurations(
                startupFrontendMs = duration(durations, TelemetryStage.STARTUP_FRONTEND),
                generationMs = duration(durations, TelemetryStage.GENERATION),
                symbolicMs = duration(durations, TelemetryStage.SYMBOLIC),
                replayMs = duration(durations, TelemetryStage.REPLAY),
            ),
            targets = finishedTargets,
        )
    }

    private fun duration(durations: Map<TelemetryStage, Long>, stage: TelemetryStage): Long =
        checkNotNull(durations[stage])

    private fun checkOpen() {
        check(!closed) { "telemetry recorder is already complete" }
    }
}

/** Stateful recorder for one target. Instances are created by [TelemetryRecorder.target]. */
class TargetTelemetryRecorder internal constructor(
    private val owner: TelemetryRecorder,
    private val methodId: String,
    private val branchId: String,
    private val capabilityLabels: List<String>,
) {
    private var machineStartedAtMs: Long? = null
    private var lastTerminalProgressAtMs: Long? = null
    private var targetReachedAtMs: Long? = null
    private var modelExtractionAtMs: Long? = null
    private var replayFinishedAtMs: Long? = null
    private var latestCounters: TargetCounters? = null
    internal var result: TargetTelemetry? = null
        private set

    fun machineStarted(counters: TargetCounters) {
        checkUnfinished()
        check(machineStartedAtMs == null) { "machine start is already recorded for $methodId/$branchId" }
        machineStartedAtMs = owner.nowRelativeMs()
        updateCounters(counters)
    }

    fun terminalProgress(counters: TargetCounters) {
        checkStarted()
        lastTerminalProgressAtMs = owner.nowRelativeMs()
        updateCounters(counters)
    }

    fun targetReached(counters: TargetCounters) {
        checkStarted()
        check(targetReachedAtMs == null) { "target reach is already recorded for $methodId/$branchId" }
        val now = owner.nowRelativeMs()
        targetReachedAtMs = now
        lastTerminalProgressAtMs = now
        updateCounters(counters)
    }

    fun modelExtractionFinished(counters: TargetCounters) {
        checkStarted()
        checkNotNull(targetReachedAtMs) { "model extraction precedes target reach for $methodId/$branchId" }
        check(modelExtractionAtMs == null) { "model extraction is already recorded for $methodId/$branchId" }
        modelExtractionAtMs = owner.nowRelativeMs()
        updateCounters(counters)
    }

    fun replayFinished(counters: TargetCounters) {
        checkStarted()
        checkNotNull(modelExtractionAtMs) { "replay precedes model extraction for $methodId/$branchId" }
        check(replayFinishedAtMs == null) { "replay finish is already recorded for $methodId/$branchId" }
        replayFinishedAtMs = owner.nowRelativeMs()
        updateCounters(counters)
    }

    fun finish(
        reason: TelemetryReasonCode,
        counters: TargetCounters? = null,
        divergence: DivergenceObservation? = null,
    ): TargetTelemetry {
        checkStarted()
        counters?.let(::updateCounters)
        val timestamps = TargetTimestamps(
            machineStartedAtMs = checkNotNull(machineStartedAtMs),
            lastTerminalProgressAtMs = lastTerminalProgressAtMs,
            targetReachedAtMs = targetReachedAtMs,
            modelExtractionAtMs = modelExtractionAtMs,
            replayFinishedAtMs = replayFinishedAtMs,
            terminalAtMs = owner.nowRelativeMs(),
        )
        return TargetTelemetry(
            methodId = methodId,
            branchId = branchId,
            status = TargetTelemetryStatus.TERMINAL,
            reason = reason,
            timestamps = timestamps,
            counters = checkNotNull(latestCounters),
            capabilityLabels = capabilityLabels.distinct().sorted(),
            divergence = divergence,
        ).also { result = it }
    }

    fun notStarted(): TargetTelemetry {
        checkUnfinished()
        check(machineStartedAtMs == null) { "a started target cannot become not_started" }
        return TargetTelemetry(
            methodId = methodId,
            branchId = branchId,
            status = TargetTelemetryStatus.NOT_STARTED,
            capabilityLabels = capabilityLabels.distinct().sorted(),
        ).also { result = it }
    }

    private fun updateCounters(next: TargetCounters) {
        val previous = latestCounters
        if (previous != null) {
            require(previous.shardId == next.shardId) { "target shard changed during execution" }
            require(next.states >= previous.states) { "state counter regressed" }
            require(next.steps >= previous.steps) { "step counter regressed" }
            if (previous.solverQueries != null && next.solverQueries != null) {
                require(next.solverQueries >= previous.solverQueries) { "solver query counter regressed" }
            }
        }
        latestCounters = if (next.solverQueries == null && previous?.solverQueries != null) {
            next.copy(solverQueries = previous.solverQueries)
        } else {
            next
        }
    }

    private fun checkStarted() {
        checkUnfinished()
        checkNotNull(machineStartedAtMs) { "machine is not started for $methodId/$branchId" }
    }

    private fun checkUnfinished() {
        check(result == null) { "target telemetry is already final for $methodId/$branchId" }
    }
}
