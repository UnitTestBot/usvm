package org.usvm.ts.pbt.telemetry

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Version of the opt-in telemetry payload embedded in a [org.usvm.ts.pbt.report.HybridReport]. */
const val TELEMETRY_SCHEMA_VERSION: Int = 1

/**
 * Stable, machine-readable terminal reasons. The serialized names are part of
 * the benchmark artifact contract and must not be derived from enum names.
 */
@Serializable
enum class TelemetryReasonCode {
    @SerialName("timeout_no_progress")
    TIMEOUT_NO_PROGRESS,

    @SerialName("global_safety_timeout")
    GLOBAL_SAFETY_TIMEOUT,

    @SerialName("unreachable_pruned")
    UNREACHABLE_PRUNED,

    @SerialName("solver_reached")
    SOLVER_REACHED,

    @SerialName("model_extraction_failed")
    MODEL_EXTRACTION_FAILED,

    @SerialName("replay_unsupported")
    REPLAY_UNSUPPORTED,

    @SerialName("replay_diverged")
    REPLAY_DIVERGED,

    @SerialName("replay_wrong_edge")
    REPLAY_WRONG_EDGE,

    @SerialName("confirmed")
    CONFIRMED,
}

/** A target is either explicitly skipped or has exactly one terminal reason. */
@Serializable
enum class TargetTelemetryStatus {
    @SerialName("not_started")
    NOT_STARTED,

    @SerialName("terminal")
    TERMINAL,
}

@Serializable
enum class DivergenceObservationKind {
    @SerialName("first_divergence")
    FIRST_DIVERGENCE,

    @SerialName("divergence_not_observable")
    DIVERGENCE_NOT_OBSERVABLE,
}

/**
 * The first concrete/symbolic mismatch when it is observable. A replay failure
 * must carry either a statement/call location or the explicit not-observable
 * marker; a missing value is never silently treated as unknown.
 */
@Serializable
data class DivergenceObservation(
    val kind: DivergenceObservationKind,
    val stmt: String? = null,
    val call: String? = null,
) {
    init {
        when (kind) {
            DivergenceObservationKind.FIRST_DIVERGENCE -> require(
                !stmt.isNullOrBlank() || !call.isNullOrBlank()
            ) { "first_divergence requires a non-blank stmt or call" }

            DivergenceObservationKind.DIVERGENCE_NOT_OBSERVABLE -> require(stmt == null && call == null) {
                "divergence_not_observable cannot carry a stmt or call"
            }
        }
    }

    companion object {
        fun first(stmt: String? = null, call: String? = null): DivergenceObservation =
            DivergenceObservation(DivergenceObservationKind.FIRST_DIVERGENCE, stmt, call)

        fun notObservable(): DivergenceObservation =
            DivergenceObservation(DivergenceObservationKind.DIVERGENCE_NOT_OBSERVABLE)
    }
}

/** Monotonic milliseconds relative to the start of the enclosing telemetry recorder. */
@Serializable
data class TargetTimestamps(
    val machineStartedAtMs: Long,
    val lastTerminalProgressAtMs: Long? = null,
    val targetReachedAtMs: Long? = null,
    val modelExtractionAtMs: Long? = null,
    val replayFinishedAtMs: Long? = null,
    val terminalAtMs: Long,
) {
    init {
        val all = listOfNotNull(
            machineStartedAtMs,
            lastTerminalProgressAtMs,
            targetReachedAtMs,
            modelExtractionAtMs,
            replayFinishedAtMs,
            terminalAtMs,
        )
        require(all.all { it >= 0 }) { "telemetry timestamps must be non-negative" }
        require(terminalAtMs >= machineStartedAtMs) { "terminal timestamp precedes machine start" }
        require(lastTerminalProgressAtMs == null || lastTerminalProgressAtMs in machineStartedAtMs..terminalAtMs) {
            "last terminal progress must be within the machine lifetime"
        }

        val orderedMilestones = listOfNotNull(targetReachedAtMs, modelExtractionAtMs, replayFinishedAtMs)
        require(orderedMilestones.all { it in machineStartedAtMs..terminalAtMs }) {
            "target milestones must be within the machine lifetime"
        }
        require(orderedMilestones.zipWithNext().all { (before, after) -> before <= after }) {
            "target milestones are not monotonic"
        }
    }
}

/** Last monotonic counter snapshot associated with a target outcome. */
@Serializable
data class TargetCounters(
    val activeRoots: Int,
    val shardId: String,
    val states: Long,
    val steps: Long,
    /** Null means that the solver integration cannot expose this counter yet. */
    val solverQueries: Long? = null,
) {
    init {
        require(activeRoots >= 0) { "activeRoots must be non-negative" }
        require(shardId.isNotBlank()) { "shardId must be non-blank" }
        require(states >= 0) { "states must be non-negative" }
        require(steps >= 0) { "steps must be non-negative" }
        require(solverQueries == null || solverQueries >= 0) { "solverQueries must be non-negative" }
    }

    companion object {
        const val UNSHARDED: String = "unsharded"
    }
}

/** Per-target terminal telemetry, keyed by stable method and EtsIR edge IDs. */
@Serializable
data class TargetTelemetry(
    val methodId: String,
    val branchId: String,
    val status: TargetTelemetryStatus,
    val reason: TelemetryReasonCode? = null,
    val timestamps: TargetTimestamps? = null,
    val counters: TargetCounters? = null,
    val capabilityLabels: List<String> = emptyList(),
    val divergence: DivergenceObservation? = null,
) {
    init {
        require(methodId.isNotBlank()) { "methodId must be non-blank" }
        require(branchId.isNotBlank()) { "branchId must be non-blank" }
        require(capabilityLabels.none(String::isBlank)) { "capability labels must be non-blank" }
        require(capabilityLabels.distinct().size == capabilityLabels.size) { "capability labels must be unique" }

        when (status) {
            TargetTelemetryStatus.NOT_STARTED -> {
                require(reason == null) { "not_started cannot have a terminal reason" }
                require(timestamps == null) { "not_started cannot have execution timestamps" }
                require(counters == null) { "not_started cannot have execution counters" }
                require(divergence == null) { "not_started cannot have a divergence" }
            }

            TargetTelemetryStatus.TERMINAL -> {
                validateTerminal()
            }
        }
    }

    private fun validateTerminal() {
        val terminalReason = requireNotNull(reason) { "terminal target requires a reason" }
        val terminalTimestamps = requireNotNull(timestamps) { "terminal target requires timestamps" }
        requireNotNull(counters) { "terminal target requires counters" }

        when (terminalReason) {
            TelemetryReasonCode.TIMEOUT_NO_PROGRESS,
            TelemetryReasonCode.GLOBAL_SAFETY_TIMEOUT,
            TelemetryReasonCode.UNREACHABLE_PRUNED,
            -> require(
                terminalTimestamps.targetReachedAtMs == null &&
                    terminalTimestamps.modelExtractionAtMs == null &&
                    terminalTimestamps.replayFinishedAtMs == null
            ) { "$terminalReason cannot have reach, extraction, or replay timestamps" }

            TelemetryReasonCode.SOLVER_REACHED -> require(
                terminalTimestamps.targetReachedAtMs != null &&
                    terminalTimestamps.modelExtractionAtMs == null &&
                    terminalTimestamps.replayFinishedAtMs == null
            ) { "solver_reached requires only a target reach timestamp" }

            TelemetryReasonCode.MODEL_EXTRACTION_FAILED -> require(
                terminalTimestamps.targetReachedAtMs != null &&
                    terminalTimestamps.modelExtractionAtMs != null &&
                    terminalTimestamps.replayFinishedAtMs == null
            ) { "model_extraction_failed requires reach and extraction timestamps" }

            TelemetryReasonCode.REPLAY_UNSUPPORTED,
            TelemetryReasonCode.REPLAY_DIVERGED,
            TelemetryReasonCode.REPLAY_WRONG_EDGE,
            TelemetryReasonCode.CONFIRMED,
            -> require(
                terminalTimestamps.targetReachedAtMs != null &&
                    terminalTimestamps.modelExtractionAtMs != null &&
                    terminalTimestamps.replayFinishedAtMs != null
            ) { "$terminalReason requires reach, extraction, and replay timestamps" }
        }

        val replayFailed = terminalReason in REPLAY_FAILURE_REASONS
        require(replayFailed == (divergence != null)) {
            if (replayFailed) {
                "$terminalReason requires first_divergence or divergence_not_observable"
            } else {
                "$terminalReason cannot have a divergence observation"
            }
        }
    }

    companion object {
        val REPLAY_FAILURE_REASONS: Set<TelemetryReasonCode> = setOf(
            TelemetryReasonCode.REPLAY_UNSUPPORTED,
            TelemetryReasonCode.REPLAY_DIVERGED,
            TelemetryReasonCode.REPLAY_WRONG_EDGE,
        )
    }
}

/** Wall-clock attribution. Values may be zero when a stage is disabled. */
@Serializable
data class StageDurations(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val startupFrontendMs: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val generationMs: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val symbolicMs: Long = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val replayMs: Long = 0,
) {
    init {
        require(startupFrontendMs >= 0) { "startup/frontend duration must be non-negative" }
        require(generationMs >= 0) { "generation duration must be non-negative" }
        require(symbolicMs >= 0) { "symbolic duration must be non-negative" }
        require(replayMs >= 0) { "replay duration must be non-negative" }
    }

    val totalMs: Long
        get() = startupFrontendMs + generationMs + symbolicMs + replayMs
}

/**
 * Opt-in telemetry payload. A complete payload closes the denominator: every
 * expected target is represented by a terminal outcome or explicit
 * [TargetTelemetryStatus.NOT_STARTED]. Partial snapshots retain the expected
 * count so interrupted runs cannot silently lose targets.
 */
@Serializable
data class HybridTelemetry(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = TELEMETRY_SCHEMA_VERSION,
    val complete: Boolean,
    val expectedTargetCount: Int,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val stageDurations: StageDurations = StageDurations(),
    val targets: List<TargetTelemetry> = emptyList(),
) {
    init {
        require(schemaVersion == TELEMETRY_SCHEMA_VERSION) {
            "unsupported telemetry schemaVersion=$schemaVersion"
        }
        require(expectedTargetCount >= 0) { "expectedTargetCount must be non-negative" }
        require(targets.size <= expectedTargetCount) { "more target outcomes than expected targets" }
        require(!complete || targets.size == expectedTargetCount) {
            "complete telemetry requires an outcome or not_started for every target"
        }
        val identities = targets.map { it.methodId to it.branchId }
        require(identities.distinct().size == identities.size) { "duplicate target telemetry identity" }
    }

    val terminalCount: Int
        get() = targets.count { it.status == TargetTelemetryStatus.TERMINAL }

    val notStartedCount: Int
        get() = targets.count { it.status == TargetTelemetryStatus.NOT_STARTED }
}
