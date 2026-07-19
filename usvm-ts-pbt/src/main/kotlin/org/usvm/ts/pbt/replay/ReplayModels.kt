package org.usvm.ts.pbt.replay

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.external.ArtifactRunConfig
import org.usvm.ts.pbt.external.ExternalCorpusReadResult
import org.usvm.ts.pbt.external.ExternalTestCase
import org.usvm.ts.pbt.external.RawRunMeta
import org.usvm.ts.pbt.external.SourceTargetRecord
import org.usvm.ts.pbt.external.TargetManifest
import org.usvm.ts.pbt.external.TargetMethod
import java.nio.file.Path

const val REPLAY_ARTIFACT_SCHEMA_VERSION: Int = 2

/** Stable machine-readable outcomes owned by the replay boundary. */
object ReplayReasonCode {
    const val CONFIRMED = "confirmed"
    const val CONFIRMED_LATE = "confirmed_late"
    const val EDGE_ALREADY_CONFIRMED = "edge_already_confirmed"
    const val EDGE_OUTSIDE_DENOMINATOR = "edge_outside_denominator"
    const val NO_DENOMINATOR_EDGE = "no_denominator_edge"

    const val REPLAY_RETURNED = "replay_returned"
    const val REPLAY_THREW = "replay_threw"
    const val REPLAY_UNSUPPORTED = "replay_unsupported"
    const val REPLAY_DIVERGED = "replay_diverged"
    const val EXECUTOR_FAILURE = "executor_failure"

    const val METHOD_OUTSIDE_DENOMINATOR = "method_outside_denominator"
    const val METHOD_UNAVAILABLE = "method_unavailable"
    const val INPUT_UNREPRESENTABLE = "input_unrepresentable"
    const val EXECUTOR_REJECTED = "executor_rejected"

    val executorTerminal: Set<String> = setOf(
        REPLAY_RETURNED,
        REPLAY_THREW,
        REPLAY_UNSUPPORTED,
        REPLAY_DIVERGED,
        EXECUTOR_FAILURE,
    )

    val reject: Set<String> = setOf(
        METHOD_OUTSIDE_DENOMINATOR,
        METHOD_UNAVAILABLE,
        INPUT_UNREPRESENTABLE,
        EXECUTOR_REJECTED,
    )

    val report: Set<String> = executorTerminal + reject + setOf(
        CONFIRMED,
        CONFIRMED_LATE,
        EDGE_ALREADY_CONFIRMED,
        EDGE_OUTSIDE_DENOMINATOR,
        NO_DENOMINATOR_EDGE,
    )
}

object ReplayRecordType {
    const val CASE = "case"
    const val EDGE = "edge"
}

object ReplayOutcome {
    const val REJECTED = "rejected"
    const val REPLAY_EXECUTED = "replay_executed"
    const val CONFIRMED = "confirmed"
    const val CONFIRMED_LATE = "confirmed_late"
}

@Serializable
data class ReplayReportRecord(
    val schemaVersion: Int = REPLAY_ARTIFACT_SCHEMA_VERSION,
    val recordType: String,
    val caseId: String,
    val methodId: String,
    val generatedAtMs: Long,
    val outcome: String,
    val reasonCode: String,
    val detail: String? = null,
    val replayStartedAtMs: Long? = null,
    val replayFinishedAtMs: Long? = null,
    val branchId: String? = null,
    /** Present only on the first concrete observation of an edge. */
    val discoveredAtMs: Long? = null,
    val fixedBudgetEligible: Boolean = false,
)

@Serializable
data class MappingReport(
    val schemaVersion: Int = REPLAY_ARTIFACT_SCHEMA_VERSION,
    val denominatorMethodIds: List<String>,
    val denominatorMethodCount: Int,
    val denominatorEdgeCount: Int,
    val exact: Int,
    val oneToMany: Int,
    val ambiguous: Int,
    val unmapped: Int,
    val synthetic: Int,
)

object StaticCapabilityLabel {
    const val SUPPORTED = "supported"
    const val UNSUPPORTED = "unsupported"
    const val NEEDS_DYNAMIC_PROBE = "needs_dynamic_probe"

    val values: Set<String> = setOf(SUPPORTED, UNSUPPORTED, NEEDS_DYNAMIC_PROBE)
}

object DynamicProbeOutcome {
    const val SUPPORTED = "supported"
    const val UNSUPPORTED = "unsupported"

    val terminalValues: Set<String> = setOf(SUPPORTED, UNSUPPORTED)
}

object TerminalCapabilityStatus {
    const val SUPPORTED = "supported"
    const val UNSUPPORTED = "unsupported"

    val values: Set<String> = setOf(SUPPORTED, UNSUPPORTED)
}

/**
 * A provider may return a partial assessment, but the pipeline rejects it
 * before publishing artifacts. This keeps `needs_dynamic_probe` useful at the
 * static stage without permitting `unknown` in a final report.
 */
data class ReplayCapabilityAssessment(
    val staticLabel: String,
    val staticReasonCode: String,
    val dynamicProbeOutcome: String? = null,
    val dynamicProbeReasonCode: String? = null,
    val terminalStatus: String? = null,
    val terminalReasonCode: String? = null,
)

@Serializable
data class CapabilityReportRecord(
    val schemaVersion: Int = REPLAY_ARTIFACT_SCHEMA_VERSION,
    val methodId: String,
    val branchId: String,
    val staticLabel: String,
    val staticReasonCode: String,
    val dynamicProbeOutcome: String? = null,
    val dynamicProbeReasonCode: String? = null,
    val terminalStatus: String,
    val terminalReasonCode: String,
)

@Serializable
data class ReplayInvariantReport(
    val confirmedSubsetReplayExecuted: Boolean,
    val replayExecutedSubsetImportedMinusRejected: Boolean,
    val residualEqualsDenominatorMinusConfirmed: Boolean,
)

@Serializable
data class DeadlineReport(
    val schemaVersion: Int = REPLAY_ARTIFACT_SCHEMA_VERSION,
    val runId: String,
    val executorId: String,
    val productionExecutor: Boolean,
    val budgetMs: Long,
    val graceMs: Long,
    val explorationDeadlineMs: Long,
    val hardResultDeadlineMs: Long,
    val rawRunFinishedAtMs: Long,
    val replayStartedAtMs: Long,
    val replayFinishedAtMs: Long,
    val overBudgetMs: Long,
    val importedCaseCount: Int,
    val rejectedCaseCount: Int,
    val replayExecutedCaseCount: Int,
    /** Cases that first confirmed at least one fixed-budget denominator edge. */
    val confirmedCaseCount: Int,
    val denominatorEdgeCount: Int,
    val fixedBudgetConfirmedEdgeCount: Int,
    /** Includes edges first observed after the hard deadline. */
    val diagnosticConfirmedEdgeCount: Int,
    val residualEdgeCount: Int,
    val lateCaseOutcomeCount: Int,
    val lateConfirmedEdgeCount: Int,
    val replayNotFinishedByDeadlineCount: Int,
    val fixedBudgetCoverage: Double,
    /** Integral of fixed-budget coverage fraction divided by [budgetMs]. */
    val coverageAuc: Double,
    val invariants: ReplayInvariantReport,
)

data class ReplayInputs(
    val rawRunDirectory: Path,
    val runConfig: Path,
    val targetManifest: Path,
    val sourceTargets: Path,
    val methodIds: Path,
    val outputDirectory: Path,
)

/** Validated, cross-linked input made available to a scene-loading integration. */
data class ValidatedReplayInput(
    val inputs: ReplayInputs,
    val runConfig: ArtifactRunConfig,
    val runMeta: RawRunMeta,
    val corpus: ExternalCorpusReadResult,
    val targetManifest: TargetManifest,
    val denominatorMethods: List<TargetMethod>,
    val denominatorTargets: List<SourceTargetRecord>,
)

data class ReplayPipelineResult(
    val replayReport: List<ReplayReportRecord>,
    val residualTargets: List<SourceTargetRecord>,
    val mappingReport: MappingReport,
    val capabilityReport: List<CapabilityReportRecord>,
    val deadlineReport: DeadlineReport,
    val validationReport: ReplayArtifactValidationReport,
)

@Serializable
data class ReplayArtifactValidationIssue(
    val path: String,
    val code: String,
    val message: String,
)

@Serializable
data class ReplayArtifactValidationReport(
    val valid: Boolean,
    val issues: List<ReplayArtifactValidationIssue>,
)

data class ReplayInputIssue(
    val artifact: String,
    val path: String,
    val code: String,
    val message: String,
)

class ReplayInputException(
    val issues: List<ReplayInputIssue>,
) : IllegalArgumentException(
    issues.joinToString(prefix = "invalid replay input: ", separator = "; ") {
        "${it.artifact}${it.path}: ${it.code}: ${it.message}"
    },
)

/** Monotonic milliseconds since the common adapter run start. */
fun interface ReplayClock {
    fun elapsedMs(): Long
}

sealed interface ReplayCaseExecution {
    data class Rejected(
        val reasonCode: String,
        val detail: String? = null,
    ) : ReplayCaseExecution

    data class Executed(
        val coveredBranchIds: Set<String>,
        val reasonCode: String,
        val detail: String? = null,
    ) : ReplayCaseExecution
}

/** The replay pipeline is independent of scene construction and method lookup. */
interface ReplayCaseExecutor {
    val id: String
    val isProduction: Boolean
    fun execute(case: ExternalTestCase): ReplayCaseExecution
}

fun interface ReplayCapabilityProvider {
    fun assess(target: SourceTargetRecord): ReplayCapabilityAssessment
}

data class ReplayRuntime(
    val executor: ReplayCaseExecutor,
    val capabilityProvider: ReplayCapabilityProvider = AllSupportedReplayCapabilities,
)

/** A-INT can load a scene from [ValidatedReplayInput] and return a runtime. */
fun interface ReplayRuntimeFactory {
    fun create(input: ValidatedReplayInput): ReplayRuntime
}

object AllSupportedReplayCapabilities : ReplayCapabilityProvider {
    override fun assess(target: SourceTargetRecord): ReplayCapabilityAssessment = ReplayCapabilityAssessment(
        staticLabel = StaticCapabilityLabel.SUPPORTED,
        staticReasonCode = "replay_executor_available",
        terminalStatus = TerminalCapabilityStatus.SUPPORTED,
        terminalReasonCode = "replay_executor_available",
    )
}
