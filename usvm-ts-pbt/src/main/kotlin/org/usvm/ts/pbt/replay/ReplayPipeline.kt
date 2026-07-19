package org.usvm.ts.pbt.replay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.usvm.ts.pbt.external.ArtifactContractCodec
import org.usvm.ts.pbt.external.ExternalTestCase
import org.usvm.ts.pbt.external.SourceTargetRecord
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

data class ReplayDeadlinePolicy(
    val budgetMs: Long,
    val graceMs: Long,
    val explorationDeadlineMs: Long,
    val hardResultDeadlineMs: Long,
) {
    companion object {
        private const val MIN_GRACE_MS: Long = 1_000
        private const val MAX_GRACE_MS: Long = 5_000
        private const val GRACE_DIVISOR: Long = 10

        fun fromBudget(budgetMs: Long): ReplayDeadlinePolicy {
            require(budgetMs > 0) { "budget must be positive" }
            val grace = minOf(MAX_GRACE_MS, maxOf(MIN_GRACE_MS, budgetMs / GRACE_DIVISOR))
            require(budgetMs > grace) { "budget $budgetMs must exceed replay grace $grace" }
            return ReplayDeadlinePolicy(
                budgetMs = budgetMs,
                graceMs = grace,
                explorationDeadlineMs = budgetMs - grace,
                hardResultDeadlineMs = budgetMs,
            )
        }
    }
}

/**
 * The sole raw-corpus -> EtsIR coverage pipeline. Adapters remain producers of
 * raw artifacts; scene loading is injected through [ReplayRuntimeFactory].
 */
class ReplayPipeline(
    private val injectedClock: ReplayClock? = null,
) {
    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    fun run(
        inputs: ReplayInputs,
        executor: ReplayCaseExecutor,
        capabilityProvider: ReplayCapabilityProvider = AllSupportedReplayCapabilities,
    ): ReplayPipelineResult = run(inputs) { ReplayRuntime(executor, capabilityProvider) }

    fun run(
        inputs: ReplayInputs,
        runtimeFactory: ReplayRuntimeFactory,
    ): ReplayPipelineResult {
        val pipelineEntryNanos = System.nanoTime()
        val validated = ReplayInputValidator.validate(inputs)
        val runtime = runtimeFactory.create(validated)
        require(runtime.executor.id.isNotBlank()) { "replay executor id must not be blank" }
        val policy = ReplayDeadlinePolicy.fromBudget(validated.runConfig.budgetMs)
        check(policy.graceMs == validated.runConfig.exportReplayGraceMs)
        check(policy.explorationDeadlineMs == validated.runConfig.explorationDeadlineMs)
        check(policy.hardResultDeadlineMs == validated.runConfig.hardResultDeadlineMs)

        val baseClock = injectedClock ?: ReplayClock {
            val pipelineElapsed = (System.nanoTime() - pipelineEntryNanos) / NANOS_PER_MILLISECOND
            saturatedAdd(validated.runMeta.totalMs, pipelineElapsed)
        }
        val clock = CheckedReplayClock(baseClock)
        val replayStartedAtMs = clock.now()
        if (replayStartedAtMs < validated.runMeta.totalMs) {
            throw ReplayInputException(
                listOf(
                    ReplayInputIssue(
                        artifact = "clock",
                        path = "$",
                        code = "before_raw_run_end",
                        message = "clock $replayStartedAtMs precedes raw run end ${validated.runMeta.totalMs}",
                    ),
                ),
            )
        }

        val capabilities = buildCapabilities(validated.denominatorTargets, runtime.capabilityProvider)
        val denominatorById = validated.denominatorTargets.associateBy { it.branchId }
        val denominatorMethodIds = validated.denominatorMethods.map { it.methodId }.toSet()
        val records = mutableListOf<ReplayReportRecord>()
        val importedCaseIds = validated.corpus.cases.map { it.id }.toSet()
        val rejectedCaseIds = mutableSetOf<String>()
        val replayExecutedCaseIds = mutableSetOf<String>()
        val fixedConfirmedCaseIds = mutableSetOf<String>()
        val diagnosticDiscovery = linkedMapOf<String, Long>()
        val fixedDiscovery = linkedMapOf<String, Long>()

        val cases = validated.corpus.cases.sortedWith(compareBy(ExternalTestCase::generatedAtMs, ExternalTestCase::id))
        cases.forEach { case ->
            val startedAt = clock.now()
            val execution = if (case.methodId !in denominatorMethodIds) {
                ReplayCaseExecution.Rejected(
                    ReplayReasonCode.METHOD_OUTSIDE_DENOMINATOR,
                    "case method '${case.methodId}' is outside the frozen method denominator",
                )
            } else {
                executeSafely(runtime.executor, case)
            }
            val finishedAt = clock.now()
            val withinBudget = finishedAt <= policy.hardResultDeadlineMs

            when (execution) {
                is ReplayCaseExecution.Rejected -> {
                    rejectedCaseIds += case.id
                    records += ReplayReportRecord(
                        recordType = ReplayRecordType.CASE,
                        caseId = case.id,
                        methodId = case.methodId,
                        generatedAtMs = case.generatedAtMs,
                        outcome = ReplayOutcome.REJECTED,
                        reasonCode = execution.reasonCode,
                        detail = execution.detail,
                        replayStartedAtMs = startedAt,
                        replayFinishedAtMs = finishedAt,
                        fixedBudgetEligible = withinBudget,
                    )
                }

                is ReplayCaseExecution.Executed -> {
                    replayExecutedCaseIds += case.id
                    records += ReplayReportRecord(
                        recordType = ReplayRecordType.CASE,
                        caseId = case.id,
                        methodId = case.methodId,
                        generatedAtMs = case.generatedAtMs,
                        outcome = ReplayOutcome.REPLAY_EXECUTED,
                        reasonCode = execution.reasonCode,
                        detail = execution.detail,
                        replayStartedAtMs = startedAt,
                        replayFinishedAtMs = finishedAt,
                        fixedBudgetEligible = withinBudget,
                    )

                    execution.coveredBranchIds.sorted().forEach { branchId ->
                        val target = denominatorById[branchId]
                        if (target == null) {
                            records += ReplayReportRecord(
                                recordType = ReplayRecordType.EDGE,
                                caseId = case.id,
                                methodId = case.methodId,
                                generatedAtMs = case.generatedAtMs,
                                outcome = ReplayOutcome.REPLAY_EXECUTED,
                                reasonCode = ReplayReasonCode.EDGE_OUTSIDE_DENOMINATOR,
                                replayStartedAtMs = startedAt,
                                replayFinishedAtMs = finishedAt,
                                branchId = branchId,
                                fixedBudgetEligible = withinBudget,
                            )
                        } else if (branchId in diagnosticDiscovery) {
                            records += ReplayReportRecord(
                                recordType = ReplayRecordType.EDGE,
                                caseId = case.id,
                                methodId = target.methodId,
                                generatedAtMs = case.generatedAtMs,
                                outcome = ReplayOutcome.REPLAY_EXECUTED,
                                reasonCode = ReplayReasonCode.EDGE_ALREADY_CONFIRMED,
                                replayStartedAtMs = startedAt,
                                replayFinishedAtMs = finishedAt,
                                branchId = branchId,
                                fixedBudgetEligible = withinBudget,
                            )
                        } else {
                            diagnosticDiscovery[branchId] = finishedAt
                            if (withinBudget) {
                                fixedDiscovery[branchId] = finishedAt
                                fixedConfirmedCaseIds += case.id
                            }
                            records += ReplayReportRecord(
                                recordType = ReplayRecordType.EDGE,
                                caseId = case.id,
                                methodId = target.methodId,
                                generatedAtMs = case.generatedAtMs,
                                outcome = if (withinBudget) ReplayOutcome.CONFIRMED else ReplayOutcome.CONFIRMED_LATE,
                                reasonCode = if (withinBudget) {
                                    ReplayReasonCode.CONFIRMED
                                } else {
                                    ReplayReasonCode.CONFIRMED_LATE
                                },
                                replayStartedAtMs = startedAt,
                                replayFinishedAtMs = finishedAt,
                                branchId = branchId,
                                discoveredAtMs = finishedAt,
                                fixedBudgetEligible = withinBudget,
                            )
                        }
                    }
                }
            }
        }

        val fixedConfirmedIds = fixedDiscovery.keys
        val residual = validated.denominatorTargets.filter { it.branchId !in fixedConfirmedIds }
        val mapping = mappingReport(
            validated.denominatorMethods.map { it.methodId },
            validated.denominatorTargets,
        )
        val importedMinusRejected = importedCaseIds - rejectedCaseIds
        val invariants = ReplayInvariantReport(
            confirmedSubsetReplayExecuted = fixedConfirmedCaseIds.all(replayExecutedCaseIds::contains),
            replayExecutedSubsetImportedMinusRejected = replayExecutedCaseIds.all(importedMinusRejected::contains),
            residualEqualsDenominatorMinusConfirmed = residual.map { it.branchId }.toSet() ==
                denominatorById.keys - fixedConfirmedIds,
        )
        check(invariants.confirmedSubsetReplayExecuted)
        check(invariants.replayExecutedSubsetImportedMinusRejected)
        check(invariants.residualEqualsDenominatorMinusConfirmed)

        prepareOutputDirectory(inputs.outputDirectory)
        inputs.outputDirectory.resolve(REPLAY_REPORT_FILE).writeText(encodeReplayRecords(records))
        inputs.outputDirectory.resolve(RESIDUAL_TARGETS_FILE).writeText(
            ArtifactContractCodec.encodeSourceTargets(residual),
        )
        inputs.outputDirectory.resolve(MAPPING_REPORT_FILE).writeText(json.encodeToString(mapping) + "\n")
        inputs.outputDirectory.resolve(CAPABILITY_REPORT_FILE).writeText(encodeCapabilities(capabilities))

        val replayFinishedAtMs = clock.now()
        val caseRecords = records.filter { it.recordType == ReplayRecordType.CASE }
        val deadline = DeadlineReport(
            runId = validated.runConfig.runId,
            executorId = runtime.executor.id,
            productionExecutor = runtime.executor.isProduction,
            budgetMs = policy.budgetMs,
            graceMs = policy.graceMs,
            explorationDeadlineMs = policy.explorationDeadlineMs,
            hardResultDeadlineMs = policy.hardResultDeadlineMs,
            rawRunFinishedAtMs = validated.runMeta.totalMs,
            replayStartedAtMs = replayStartedAtMs,
            replayFinishedAtMs = replayFinishedAtMs,
            overBudgetMs = maxOf(
                validated.runMeta.overBudgetMs,
                (replayFinishedAtMs - policy.hardResultDeadlineMs).coerceAtLeast(0),
            ),
            importedCaseCount = importedCaseIds.size,
            rejectedCaseCount = rejectedCaseIds.size,
            replayExecutedCaseCount = replayExecutedCaseIds.size,
            confirmedCaseCount = fixedConfirmedCaseIds.size,
            denominatorEdgeCount = denominatorById.size,
            fixedBudgetConfirmedEdgeCount = fixedDiscovery.size,
            diagnosticConfirmedEdgeCount = diagnosticDiscovery.size,
            residualEdgeCount = residual.size,
            lateCaseOutcomeCount = caseRecords.count { it.replayFinishedAtMs!! > policy.hardResultDeadlineMs },
            lateConfirmedEdgeCount = diagnosticDiscovery.count { (_, time) -> time > policy.hardResultDeadlineMs },
            replayNotFinishedByDeadlineCount = caseRecords.count {
                it.replayFinishedAtMs!! > policy.hardResultDeadlineMs
            },
            fixedBudgetCoverage = coverageFraction(fixedDiscovery.size, denominatorById.size),
            coverageAuc = coverageAuc(fixedDiscovery.values, denominatorById.size, policy.hardResultDeadlineMs),
            invariants = invariants,
        )
        inputs.outputDirectory.resolve(DEADLINE_REPORT_FILE).writeText(json.encodeToString(deadline) + "\n")

        val validation = ReplayArtifactValidator.validateOutputDirectory(inputs.outputDirectory)
        check(validation.valid) { "replay pipeline wrote invalid output: ${validation.issues}" }
        return ReplayPipelineResult(records, residual, mapping, capabilities, deadline, validation)
    }

    private fun executeSafely(executor: ReplayCaseExecutor, case: ExternalTestCase): ReplayCaseExecution {
        val raw = try {
            executor.execute(case)
        } catch (cause: Exception) {
            ReplayCaseExecution.Executed(
                coveredBranchIds = emptySet(),
                reasonCode = ReplayReasonCode.EXECUTOR_FAILURE,
                detail = cause.message ?: cause::class.simpleName ?: "executor failure",
            )
        }
        return when (raw) {
            is ReplayCaseExecution.Rejected -> {
                if (raw.reasonCode in ReplayReasonCode.reject) {
                    raw
                } else {
                    ReplayCaseExecution.Rejected(
                        ReplayReasonCode.EXECUTOR_REJECTED,
                        "executor returned unknown reject reason '${raw.reasonCode}': ${raw.detail.orEmpty()}",
                    )
                }
            }

            is ReplayCaseExecution.Executed -> {
                if (raw.reasonCode in ReplayReasonCode.executorTerminal) {
                    raw
                } else {
                    ReplayCaseExecution.Executed(
                        raw.coveredBranchIds,
                        ReplayReasonCode.EXECUTOR_FAILURE,
                        "executor returned unknown terminal reason '${raw.reasonCode}': ${raw.detail.orEmpty()}",
                    )
                }
            }
        }
    }

    private fun buildCapabilities(
        targets: List<SourceTargetRecord>,
        provider: ReplayCapabilityProvider,
    ): List<CapabilityReportRecord> = targets.map { target ->
        val assessment = provider.assess(target)
        val issue = capabilityIssue(target, assessment)
        if (issue != null) throw ReplayInputException(listOf(issue))
        CapabilityReportRecord(
            methodId = target.methodId,
            branchId = target.branchId,
            staticLabel = assessment.staticLabel,
            staticReasonCode = assessment.staticReasonCode,
            dynamicProbeOutcome = assessment.dynamicProbeOutcome,
            dynamicProbeReasonCode = assessment.dynamicProbeReasonCode,
            terminalStatus = checkNotNull(assessment.terminalStatus),
            terminalReasonCode = checkNotNull(assessment.terminalReasonCode),
        )
    }

    private fun capabilityIssue(
        target: SourceTargetRecord,
        assessment: ReplayCapabilityAssessment,
    ): ReplayInputIssue? {
        val prefix = "$[${target.methodId},${target.branchId}]"
        fun issue(code: String, message: String) = ReplayInputIssue("capability", prefix, code, message)
        if (assessment.staticLabel !in StaticCapabilityLabel.values) {
            return issue("unknown_static_label", "unknown static capability label '${assessment.staticLabel}'")
        }
        if (assessment.staticReasonCode.isBlank()) return issue("blank_reason", "static reason code is blank")
        if (assessment.terminalStatus !in TerminalCapabilityStatus.values) {
            return issue("capability_non_terminal", "final capability must be supported or unsupported")
        }
        if (assessment.terminalReasonCode.isNullOrBlank()) {
            return issue("blank_reason", "terminal capability reason code is blank")
        }
        if (assessment.staticLabel == StaticCapabilityLabel.NEEDS_DYNAMIC_PROBE) {
            if (assessment.dynamicProbeOutcome !in DynamicProbeOutcome.terminalValues) {
                return issue("dynamic_probe_missing", "needs_dynamic_probe requires a terminal dynamic outcome")
            }
            if (assessment.dynamicProbeReasonCode.isNullOrBlank()) {
                return issue("blank_reason", "dynamic probe reason code is blank")
            }
            if (assessment.dynamicProbeOutcome != assessment.terminalStatus) {
                return issue("probe_terminal_mismatch", "dynamic probe outcome differs from terminal status")
            }
        } else {
            if (assessment.staticLabel != assessment.terminalStatus) {
                return issue("static_terminal_mismatch", "terminal status differs from a terminal static label")
            }
            if (assessment.dynamicProbeOutcome != null || assessment.dynamicProbeReasonCode != null) {
                return issue("unexpected_dynamic_probe", "terminal static label must not carry a dynamic probe")
            }
        }
        return null
    }

    private fun mappingReport(methodIds: List<String>, targets: List<SourceTargetRecord>): MappingReport {
        val counts = targets.groupingBy { it.mappingStatus }.eachCount()
        return MappingReport(
            denominatorMethodIds = methodIds,
            denominatorMethodCount = methodIds.size,
            denominatorEdgeCount = targets.size,
            exact = counts["exact"] ?: 0,
            oneToMany = counts["oneToMany"] ?: 0,
            ambiguous = counts["ambiguous"] ?: 0,
            unmapped = counts["unmapped"] ?: 0,
            synthetic = counts["synthetic"] ?: 0,
        )
    }

    private fun coverageAuc(discoveryTimes: Collection<Long>, denominator: Int, budgetMs: Long): Double {
        if (denominator == 0 || budgetMs <= 0) return 0.0
        var previous = 0L
        var covered = 0
        var coveredEdgeMs = 0.0
        discoveryTimes.groupingBy { it }.eachCount().toSortedMap().forEach { (time, count) ->
            val bounded = time.coerceIn(previous, budgetMs)
            coveredEdgeMs += covered.toDouble() * (bounded - previous).toDouble()
            covered += count
            previous = bounded
        }
        coveredEdgeMs += covered.toDouble() * (budgetMs - previous).toDouble()
        return coveredEdgeMs / denominator.toDouble() / budgetMs.toDouble()
    }

    private fun coverageFraction(covered: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else covered.toDouble() / denominator.toDouble()

    private fun encodeReplayRecords(values: List<ReplayReportRecord>): String = buildString {
        values.forEach { appendLine(json.encodeToString(it)) }
    }

    private fun encodeCapabilities(values: List<CapabilityReportRecord>): String = buildString {
        values.forEach { appendLine(json.encodeToString(it)) }
    }

    private fun prepareOutputDirectory(path: Path) {
        if (path.exists() && !path.isDirectory()) error("replay output $path is not a directory")
        path.createDirectories()
        val unexpected = path.listDirectoryEntries().map(Path::name).toSet() - REPLAY_OUTPUT_FILES
        require(unexpected.isEmpty()) {
            "replay output $path contains unrelated entries: ${unexpected.sorted().joinToString()}"
        }
    }

    private class CheckedReplayClock(private val delegate: ReplayClock) {
        private var previous: Long = -1

        fun now(): Long {
            val current = delegate.elapsedMs()
            require(current >= 0) { "replay clock returned negative elapsed time $current" }
            require(current >= previous) { "replay clock moved backwards: $previous -> $current" }
            previous = current
            return current
        }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND: Long = 1_000_000

        fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}

const val REPLAY_REPORT_FILE: String = "replay-report.jsonl"
const val RESIDUAL_TARGETS_FILE: String = "residual-targets.jsonl"
const val MAPPING_REPORT_FILE: String = "mapping-report.json"
const val CAPABILITY_REPORT_FILE: String = "capability-report.jsonl"
const val DEADLINE_REPORT_FILE: String = "deadline-report.json"

val REPLAY_OUTPUT_FILES: Set<String> = setOf(
    REPLAY_REPORT_FILE,
    RESIDUAL_TARGETS_FILE,
    MAPPING_REPORT_FILE,
    CAPABILITY_REPORT_FILE,
    DEADLINE_REPORT_FILE,
)
