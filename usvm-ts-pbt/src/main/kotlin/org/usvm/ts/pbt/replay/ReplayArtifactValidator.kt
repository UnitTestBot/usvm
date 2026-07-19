package org.usvm.ts.pbt.replay

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.usvm.ts.pbt.external.SourceTargetRecord
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.abs

/** Self-contained validator for the five final replay artifacts. */
object ReplayArtifactValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun validateOutputDirectory(path: Path): ReplayArtifactValidationReport {
        val issues = mutableListOf<ReplayArtifactValidationIssue>()
        if (!path.exists() || !path.isDirectory()) {
            issues.error("$", "not_directory", "$path is not a replay output directory")
            return ReplayArtifactValidationReport(false, issues)
        }
        val entries = path.listDirectoryEntries().associateBy(Path::name)
        REPLAY_OUTPUT_FILES.sorted().forEach { name ->
            if (entries[name]?.isRegularFile() != true) {
                issues.error("$/$name", "missing_artifact", "required replay artifact is absent")
            }
        }
        (entries.keys - REPLAY_OUTPUT_FILES).sorted().forEach { name ->
            issues.error("$/$name", "unexpected_artifact", "replay output contains an extra entry")
        }
        if (issues.isNotEmpty()) return ReplayArtifactValidationReport(false, issues)

        val replay = decodeLines<ReplayReportRecord>(path.resolve(REPLAY_REPORT_FILE), issues)
        val residual = decodeLines<SourceTargetRecord>(path.resolve(RESIDUAL_TARGETS_FILE), issues)
        val capabilities = decodeLines<CapabilityReportRecord>(path.resolve(CAPABILITY_REPORT_FILE), issues)
        val mapping = decodeDocument<MappingReport>(path.resolve(MAPPING_REPORT_FILE), issues)
        val deadline = decodeDocument<DeadlineReport>(path.resolve(DEADLINE_REPORT_FILE), issues)
        if (mapping == null || deadline == null) return ReplayArtifactValidationReport(false, issues)

        validateReplayRecords(replay, deadline, issues)
        validateCapabilities(capabilities, issues)
        validateCrossArtifact(replay, residual, capabilities, mapping, deadline, issues)
        return ReplayArtifactValidationReport(issues.isEmpty(), issues)
    }

    private fun validateReplayRecords(
        records: List<ReplayReportRecord>,
        deadline: DeadlineReport,
        issues: MutableList<ReplayArtifactValidationIssue>,
    ) {
        records.forEachIndexed { index, record ->
            val path = "$REPLAY_REPORT_FILE[${index + 1}]"
            if (record.schemaVersion != REPLAY_ARTIFACT_SCHEMA_VERSION) {
                issues.error(path, "schema_version", "unsupported replay record schemaVersion ${record.schemaVersion}")
            }
            if (record.recordType !in setOf(ReplayRecordType.CASE, ReplayRecordType.EDGE)) {
                issues.error("$path.recordType", "unknown_enum", "unknown replay record type '${record.recordType}'")
            }
            if (record.caseId.isBlank() || record.methodId.isBlank()) {
                issues.error(path, "blank_identity", "caseId and methodId must not be blank")
            }
            if (record.generatedAtMs < 0) issues.error("$path.generatedAtMs", "out_of_range", "must be non-negative")
            if (record.reasonCode !in ReplayReasonCode.report) {
                issues.error("$path.reasonCode", "unknown_reason", "unknown replay reason '${record.reasonCode}'")
            }
            val started = record.replayStartedAtMs
            val finished = record.replayFinishedAtMs
            if (started == null || finished == null || started < 0 || finished < started) {
                issues.error(
                    path,
                    "invalid_replay_time",
                    "replay timestamps must be present, non-negative, and ordered",
                )
            }
            if (record.recordType == ReplayRecordType.CASE) {
                if (record.branchId != null || record.discoveredAtMs != null) {
                    issues.error(path, "invalid_case_record", "case record cannot carry branch discovery fields")
                }
                if (record.outcome !in setOf(ReplayOutcome.REJECTED, ReplayOutcome.REPLAY_EXECUTED)) {
                    issues.error("$path.outcome", "invalid_case_outcome", "invalid case outcome '${record.outcome}'")
                }
                if (record.outcome == ReplayOutcome.REJECTED && record.reasonCode !in ReplayReasonCode.reject) {
                    issues.error("$path.reasonCode", "invalid_reject_reason", "rejected case has a non-reject reason")
                }
                if (record.outcome == ReplayOutcome.REPLAY_EXECUTED &&
                    record.reasonCode !in ReplayReasonCode.executorTerminal
                ) {
                    issues.error(
                        "$path.reasonCode",
                        "invalid_terminal_reason",
                        "executed case has a non-terminal reason",
                    )
                }
            } else {
                if (record.branchId.isNullOrBlank()) {
                    issues.error("$path.branchId", "missing_branch", "edge record must carry branchId")
                }
                when (record.outcome) {
                    ReplayOutcome.CONFIRMED -> {
                        if (record.reasonCode != ReplayReasonCode.CONFIRMED || !record.fixedBudgetEligible) {
                            issues.error(path, "invalid_confirmation", "fixed confirmation has inconsistent fields")
                        }
                        if (record.discoveredAtMs == null || record.discoveredAtMs > deadline.hardResultDeadlineMs) {
                            issues.error(
                                path,
                                "late_fixed_confirmation",
                                "fixed confirmation must finish by the deadline",
                            )
                        }
                    }

                    ReplayOutcome.CONFIRMED_LATE -> {
                        if (record.reasonCode != ReplayReasonCode.CONFIRMED_LATE || record.fixedBudgetEligible) {
                            issues.error(path, "invalid_late_confirmation", "late confirmation has inconsistent fields")
                        }
                        if (record.discoveredAtMs == null || record.discoveredAtMs <= deadline.hardResultDeadlineMs) {
                            issues.error(
                                path,
                                "not_late",
                                "late confirmation must finish after the deadline",
                            )
                        }
                    }

                    ReplayOutcome.REPLAY_EXECUTED -> {
                        if (record.reasonCode !in setOf(
                                ReplayReasonCode.EDGE_ALREADY_CONFIRMED,
                                ReplayReasonCode.EDGE_OUTSIDE_DENOMINATOR,
                            )
                        ) {
                            issues.error(path, "invalid_edge_outcome", "non-confirming edge has an invalid reason")
                        }
                        if (record.discoveredAtMs != null) {
                            issues.error(
                                path,
                                "duplicate_discovery_time",
                                "only first confirmation carries discoveredAtMs",
                            )
                        }
                    }

                    else -> {
                        issues.error(
                            "$path.outcome",
                            "invalid_edge_outcome",
                            "invalid edge outcome '${record.outcome}'",
                        )
                    }
                }
                if (record.discoveredAtMs != null && record.discoveredAtMs != record.replayFinishedAtMs) {
                    issues.error(
                        path,
                        "discovery_time_mismatch",
                        "discoveredAtMs must equal incremental case replay finish",
                    )
                }
            }
        }

        val caseRecords = records.filter { it.recordType == ReplayRecordType.CASE }
        val duplicateCases = caseRecords.groupingBy { it.caseId }.eachCount().filterValues { it != 1 }
        duplicateCases.keys.sorted().forEach { id ->
            issues.error(REPLAY_REPORT_FILE, "duplicate_case", "case '$id' does not have exactly one case record")
        }
        val actualOrder = caseRecords.map { it.generatedAtMs to it.caseId }
        if (actualOrder != actualOrder.sortedWith(compareBy({ it.first }, { it.second }))) {
            issues.error(REPLAY_REPORT_FILE, "case_order", "cases are not ordered by (generatedAtMs, caseId)")
        }
        val executedIds = caseRecords.filter { it.outcome == ReplayOutcome.REPLAY_EXECUTED }.map { it.caseId }.toSet()
        records.filter { it.recordType == ReplayRecordType.EDGE }.forEachIndexed { index, edge ->
            if (edge.caseId !in executedIds) {
                issues.error(
                    "$REPLAY_REPORT_FILE.edge[$index]",
                    "edge_without_replay",
                    "edge belongs to a non-executed case",
                )
            }
        }
        val firstDiscoveries = records.filter { it.discoveredAtMs != null }
        val duplicateDiscoveries = firstDiscoveries.groupingBy { it.branchId }.eachCount().filterValues { it != 1 }
        duplicateDiscoveries.keys.forEach { branchId ->
            issues.error(REPLAY_REPORT_FILE, "duplicate_edge_discovery", "edge '$branchId' has multiple discoveries")
        }
    }

    private fun validateCapabilities(
        records: List<CapabilityReportRecord>,
        issues: MutableList<ReplayArtifactValidationIssue>,
    ) {
        val keys = mutableSetOf<Pair<String, String>>()
        records.forEachIndexed { index, record ->
            val path = "$CAPABILITY_REPORT_FILE[${index + 1}]"
            if (record.schemaVersion != REPLAY_ARTIFACT_SCHEMA_VERSION) {
                issues.error(path, "schema_version", "unsupported capability schemaVersion ${record.schemaVersion}")
            }
            if (!keys.add(record.methodId to record.branchId)) {
                issues.error(path, "duplicate", "duplicate capability key")
            }
            if (record.staticLabel !in StaticCapabilityLabel.values) {
                issues.error("$path.staticLabel", "unknown_enum", "unknown static capability label")
            }
            if (record.staticReasonCode.isBlank() || record.terminalReasonCode.isBlank()) {
                issues.error(path, "blank_reason", "static and terminal reason codes are required")
            }
            if (record.terminalStatus !in TerminalCapabilityStatus.values) {
                issues.error("$path.terminalStatus", "capability_non_terminal", "terminal capability cannot be unknown")
            }
            if (record.staticLabel == StaticCapabilityLabel.NEEDS_DYNAMIC_PROBE) {
                if (record.dynamicProbeOutcome !in DynamicProbeOutcome.terminalValues) {
                    issues.error(path, "dynamic_probe_missing", "needs_dynamic_probe has no terminal probe outcome")
                }
                if (record.dynamicProbeReasonCode.isNullOrBlank()) {
                    issues.error(path, "blank_reason", "dynamic probe reason code is required")
                }
                if (record.dynamicProbeOutcome != record.terminalStatus) {
                    issues.error(path, "probe_terminal_mismatch", "probe outcome differs from terminal status")
                }
            } else {
                if (record.staticLabel != record.terminalStatus) {
                    issues.error(path, "static_terminal_mismatch", "terminal status differs from terminal static label")
                }
                if (record.dynamicProbeOutcome != null || record.dynamicProbeReasonCode != null) {
                    issues.error(path, "unexpected_dynamic_probe", "terminal static label carries a dynamic probe")
                }
            }
        }
    }

    private fun validateCrossArtifact(
        replay: List<ReplayReportRecord>,
        residual: List<SourceTargetRecord>,
        capabilities: List<CapabilityReportRecord>,
        mapping: MappingReport,
        deadline: DeadlineReport,
        issues: MutableList<ReplayArtifactValidationIssue>,
    ) {
        if (mapping.schemaVersion != REPLAY_ARTIFACT_SCHEMA_VERSION ||
            deadline.schemaVersion != REPLAY_ARTIFACT_SCHEMA_VERSION
        ) {
            issues.error("$", "schema_version", "document replay artifacts must use schemaVersion 2")
        }
        if (deadline.runId.isBlank() || deadline.executorId.isBlank()) {
            issues.error(DEADLINE_REPORT_FILE, "blank_identity", "runId and executorId must not be blank")
        }
        val mappingSum = mapping.exact + mapping.oneToMany + mapping.ambiguous + mapping.unmapped + mapping.synthetic
        if (mappingSum != mapping.denominatorEdgeCount) {
            issues.error(MAPPING_REPORT_FILE, "mapping_count", "mapping counts do not sum to denominator")
        }
        if (mapping.denominatorMethodIds.size != mapping.denominatorMethodCount ||
            mapping.denominatorMethodIds.toSet().size != mapping.denominatorMethodIds.size ||
            mapping.denominatorMethodIds.any(String::isBlank)
        ) {
            issues.error(MAPPING_REPORT_FILE, "method_count", "mapping method IDs are blank, duplicate, or miscounted")
        }

        val caseRecords = replay.filter { it.recordType == ReplayRecordType.CASE }
        val importedIds = caseRecords.map { it.caseId }.toSet()
        val rejectedIds = caseRecords.filter { it.outcome == ReplayOutcome.REJECTED }.map { it.caseId }.toSet()
        val executedIds = caseRecords.filter { it.outcome == ReplayOutcome.REPLAY_EXECUTED }.map { it.caseId }.toSet()
        val fixedRecords = replay.filter { it.outcome == ReplayOutcome.CONFIRMED }
        val lateRecords = replay.filter { it.outcome == ReplayOutcome.CONFIRMED_LATE }
        val fixedIds = fixedRecords.mapNotNull { it.branchId }.toSet()
        val diagnosticIds = (fixedRecords + lateRecords).mapNotNull { it.branchId }.toSet()
        val fixedCaseIds = fixedRecords.map { it.caseId }.toSet()
        val residualKeys = residual.map { it.methodId to it.branchId }.toSet()
        val fixedKeys = fixedRecords.map { it.methodId to checkNotNull(it.branchId) }.toSet()
        val capabilityKeys = capabilities.map { it.methodId to it.branchId }.toSet()

        residual.forEachIndexed { index, target ->
            if (target.schemaVersion != REPLAY_ARTIFACT_SCHEMA_VERSION) {
                issues.error("$RESIDUAL_TARGETS_FILE[${index + 1}]", "schema_version", "invalid residual schemaVersion")
            }
        }
        if (fixedKeys.intersect(residualKeys).isNotEmpty() || fixedKeys + residualKeys != capabilityKeys) {
            issues.error(
                RESIDUAL_TARGETS_FILE,
                "residual_mismatch",
                "residual must equal denominator capability keys minus fixed-budget confirmations",
            )
        }
        if (capabilityKeys.size != mapping.denominatorEdgeCount ||
            capabilityKeys.size != deadline.denominatorEdgeCount
        ) {
            issues.error("$", "denominator_mismatch", "mapping, capability, and deadline denominators differ")
        }
        if (!mapping.denominatorMethodIds.toSet().containsAll(capabilityKeys.map { it.first })) {
            issues.error(
                MAPPING_REPORT_FILE,
                "method_count",
                "capability keys contain a method outside the denominator",
            )
        }

        val confirmedSubset = fixedCaseIds.all(executedIds::contains)
        val executedSubset = executedIds.all((importedIds - rejectedIds)::contains)
        val residualInvariant = fixedKeys + residualKeys == capabilityKeys &&
            fixedKeys.intersect(residualKeys).isEmpty()
        if (!confirmedSubset || !executedSubset || !residualInvariant) {
            issues.error(
                DEADLINE_REPORT_FILE,
                "funnel_invariant",
                "confirmed/replayed/imported or residual invariant failed",
            )
        }
        if (
            deadline.invariants.confirmedSubsetReplayExecuted != confirmedSubset ||
            deadline.invariants.replayExecutedSubsetImportedMinusRejected != executedSubset ||
            deadline.invariants.residualEqualsDenominatorMinusConfirmed != residualInvariant
        ) {
            issues.error(
                DEADLINE_REPORT_FILE,
                "invariant_report_mismatch",
                "reported invariants differ from artifacts",
            )
        }

        val expectedLateCases = caseRecords.count { it.replayFinishedAtMs!! > deadline.hardResultDeadlineMs }
        val expectedCoverage = if (capabilityKeys.isEmpty()) 0.0 else fixedIds.size.toDouble() / capabilityKeys.size
        val expectedAuc = coverageAuc(
            fixedRecords.mapNotNull { it.discoveredAtMs },
            capabilityKeys.size,
            deadline.hardResultDeadlineMs,
        )
        val scalarMismatches = listOf(
            deadline.importedCaseCount != importedIds.size,
            deadline.rejectedCaseCount != rejectedIds.size,
            deadline.replayExecutedCaseCount != executedIds.size,
            deadline.confirmedCaseCount != fixedCaseIds.size,
            deadline.fixedBudgetConfirmedEdgeCount != fixedIds.size,
            deadline.diagnosticConfirmedEdgeCount != diagnosticIds.size,
            deadline.residualEdgeCount != residualKeys.size,
            deadline.lateCaseOutcomeCount != expectedLateCases,
            deadline.replayNotFinishedByDeadlineCount != expectedLateCases,
            deadline.lateConfirmedEdgeCount != lateRecords.size,
            abs(deadline.fixedBudgetCoverage - expectedCoverage) > 1e-12,
            abs(deadline.coverageAuc - expectedAuc) > 1e-12,
        )
        if (scalarMismatches.any { it }) {
            issues.error(
                DEADLINE_REPORT_FILE,
                "count_mismatch",
                "deadline counts or coverage metrics differ from artifacts",
            )
        }

        val expectedPolicy = runCatching { ReplayDeadlinePolicy.fromBudget(deadline.budgetMs) }.getOrNull()
        if (expectedPolicy == null ||
            deadline.graceMs != expectedPolicy.graceMs ||
            deadline.explorationDeadlineMs != expectedPolicy.explorationDeadlineMs ||
            deadline.hardResultDeadlineMs != expectedPolicy.hardResultDeadlineMs
        ) {
            issues.error(DEADLINE_REPORT_FILE, "deadline_formula", "deadline does not use the common B/G formula")
        }
        if (deadline.replayFinishedAtMs < deadline.replayStartedAtMs ||
            deadline.overBudgetMs < (deadline.replayFinishedAtMs - deadline.hardResultDeadlineMs).coerceAtLeast(0)
        ) {
            issues.error(DEADLINE_REPORT_FILE, "deadline_time", "replay finish or over-budget accounting is invalid")
        }
    }

    private fun coverageAuc(discoveries: Collection<Long>, denominator: Int, budgetMs: Long): Double {
        if (denominator == 0 || budgetMs <= 0) return 0.0
        var previous = 0L
        var covered = 0
        var area = 0.0
        discoveries.groupingBy { it }.eachCount().toSortedMap().forEach { (time, count) ->
            val bounded = time.coerceIn(previous, budgetMs)
            area += covered.toDouble() * (bounded - previous).toDouble()
            covered += count
            previous = bounded
        }
        area += covered.toDouble() * (budgetMs - previous).toDouble()
        return area / denominator.toDouble() / budgetMs.toDouble()
    }

    private inline fun <reified T> decodeLines(
        path: Path,
        issues: MutableList<ReplayArtifactValidationIssue>,
    ): List<T> = path.readText()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapIndexedNotNull { index, line ->
            runCatching { json.decodeFromString<T>(line) }.getOrElse { cause ->
                issues.error("${path.name}[${index + 1}]", "decode_error", shortMessage(cause))
                null
            }
        }.toList()

    private inline fun <reified T> decodeDocument(
        path: Path,
        issues: MutableList<ReplayArtifactValidationIssue>,
    ): T? = runCatching { json.decodeFromString<T>(path.readText()) }.getOrElse { cause ->
        issues.error(path.name, "decode_error", shortMessage(cause))
        null
    }

    private fun MutableList<ReplayArtifactValidationIssue>.error(path: String, code: String, message: String) {
        this += ReplayArtifactValidationIssue(path, code, message)
    }

    private fun shortMessage(cause: Throwable): String =
        cause.message?.lineSequence()?.firstOrNull() ?: cause::class.simpleName ?: "decode failure"
}
