package org.usvm.stopstrategies

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeSource

/**
 * Stable termination reasons reported by [ProgressBasedStopStrategy].
 */
enum class ProgressBasedStopReason(val code: String) {
    TARGETS_REACHED("targets_reached"),
    NO_PROGRESS_TIMEOUT("timeout_no_progress"),
    GLOBAL_SAFETY_TIMEOUT("global_safety_timeout"),
}

/**
 * Stops execution after [progressTimeout] without observable progress, or after the independent
 * [globalSafetyTimeout] ceiling.
 *
 * The strategy is deliberately generic: [progressCounter] may represent removed terminal targets,
 * collected states, covered instructions, or any other monotonically increasing value. [isComplete]
 * retains the completion semantics of the strategy this one replaces; for target collections it
 * should normally be `targets.all { it.isRemoved }`, which also preserves immediate completion for
 * an empty target collection.
 *
 * Time measurement starts on the first [shouldStop] call. [clock] must return finite, monotonically
 * non-decreasing values. A regression is rejected instead of silently extending the progress window.
 * The production clock uses [TimeSource.Monotonic]; callers may inject a deterministic clock in tests.
 */
class ProgressBasedStopStrategy(
    private val progressTimeout: Duration,
    private val progressCounter: () -> Long,
    private val globalSafetyTimeout: Duration = Duration.INFINITE,
    private val isComplete: () -> Boolean = { false },
    private val clock: () -> Duration = monotonicElapsedClock(),
) : StopStrategy {
    init {
        require(progressTimeout > ZERO && progressTimeout.isFinite()) {
            "Progress timeout must be positive and finite, got $progressTimeout"
        }
        require(globalSafetyTimeout >= ZERO) {
            "Global safety timeout must be non-negative, got $globalSafetyTimeout"
        }
    }

    var terminationReason: ProgressBasedStopReason? = null
        private set

    private var startedAt: Duration? = null
    private var lastProgressAt: Duration? = null
    private var lastClockSample: Duration? = null
    private var lastProgress: Long? = null

    override fun shouldStop(): Boolean {
        if (terminationReason != null) {
            return true
        }

        if (isComplete()) {
            terminationReason = ProgressBasedStopReason.TARGETS_REACHED
            return true
        }

        val now = clock()
        check(now.isFinite()) { "Monotonic clock must return a finite value, got $now" }

        val previousClockSample = lastClockSample
        check(previousClockSample == null || now >= previousClockSample) {
            "Monotonic clock regressed from $previousClockSample to $now"
        }
        lastClockSample = now

        val progress = progressCounter()
        val previousProgress = lastProgress
        check(previousProgress == null || progress >= previousProgress) {
            "Progress counter regressed from $previousProgress to $progress"
        }

        if (startedAt == null) {
            startedAt = now
            lastProgressAt = now
        } else if (progress > requireNotNull(previousProgress)) {
            lastProgressAt = now
        }
        lastProgress = progress

        val globalElapsed = now - requireNotNull(startedAt)
        if (globalElapsed >= globalSafetyTimeout) {
            terminationReason = ProgressBasedStopReason.GLOBAL_SAFETY_TIMEOUT
            return true
        }

        val noProgressElapsed = now - requireNotNull(lastProgressAt)
        if (noProgressElapsed >= progressTimeout) {
            terminationReason = ProgressBasedStopReason.NO_PROGRESS_TIMEOUT
            return true
        }

        return false
    }

    override fun stopReason(): String = terminationReason?.let { "Stop reason: ${it.code}" }
        ?: "Stop reason: ${this::class.simpleName ?: this}"
}

private fun monotonicElapsedClock(): () -> Duration {
    val origin = TimeSource.Monotonic.markNow()
    return origin::elapsedNow
}
