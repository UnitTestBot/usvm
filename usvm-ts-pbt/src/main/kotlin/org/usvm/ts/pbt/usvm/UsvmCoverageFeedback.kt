package org.usvm.ts.pbt.usvm

import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.model.JsConcreteValue

/** Concrete feedback for the original property analysis. A replay must execute exactly [inputs]. */
data class UsvmCoverageFeedback(
    val initialCoverage: PropertyCoverageArtifact?,
    val replay: (inputs: List<JsConcreteValue>) -> PropertyRunResult?,
)

/** Outcome of applying a later native report to the original property session. */
enum class UsvmCoverageUpdateStatus {
    ACCEPTED,
    STALE,
    INCOMPATIBLE,
    NO_ADMITTED_INPUTS,
    UNSUPPORTED,
    SOURCE_CHANGED,
}

/** Only accepted, exactly mapped native hits can appear in [additions]. */
data class UsvmCoverageUpdateResult(
    val status: UsvmCoverageUpdateStatus,
    val additions: Set<UsvmCoverageEdge> = emptySet(),
)

/** Why a report can or cannot supply exact coverage goals for this property/source snapshot. */
enum class UsvmCoverageEvidenceStatus {
    ABSENT,
    STALE,
    UNSUPPORTED,
    NO_ADMITTED_INPUTS,
    EXACT,
    PARTIAL,
}

/** Distinguishes a CFG edge from either of its endpoint statements. */
data class UsvmCoverageEdge(
    val method: String,
    val conditionIndex: Int,
    val successorIndex: Int,
    val outcome: Boolean,
)

/** A real queue decision made during the original predicate analysis. */
data class UsvmEdgeSelection(
    val evidenceRevision: Int,
    val stateId: UInt,
    val statement: String,
    val pendingStates: Int,
    val goal: UsvmCoverageEdge?,
    val distance: Int?,
    val originalObjectiveTurn: Boolean,
    val traversedGoals: Set<UsvmCoverageEdge>,
)

/** Concrete confirmation outcomes; none except CONFIRMED can add edges. */
enum class UsvmEdgeReplayStatus {
    CONFIRMED,
    EDGE_MISMATCH,
    REJECTED_OR_INCOMPATIBLE,
    NOT_RUN,
    REPLAY_FAILED,
}

/** A bounded exact-input native replay and its effect on the shared edge set. */
data class UsvmEdgeReplay(
    val inputs: List<JsConcreteValue>,
    val status: UsvmEdgeReplayStatus,
    val additions: Set<UsvmCoverageEdge>,
    val remainingGoals: Set<UsvmCoverageEdge>,
    val elapsedMillis: Long,
)

/** Bounded history; symbolic reachability never updates [confirmedCovered]. */
data class UsvmEdgeGuidanceTrace(
    val evidenceStatus: UsvmCoverageEvidenceStatus,
    val initialCovered: Set<UsvmCoverageEdge>,
    val initialGoals: Set<UsvmCoverageEdge>,
    val confirmedCovered: Set<UsvmCoverageEdge>,
    val remainingGoals: Set<UsvmCoverageEdge>,
    val attemptedGoals: Set<UsvmCoverageEdge>,
    val attemptLimitPerGoal: Int,
    val selections: List<UsvmEdgeSelection>,
    val omittedSelections: Int,
    val replays: List<UsvmEdgeReplay>,
    val diagnostics: List<String>,
    val elapsedMillis: Long,
)
