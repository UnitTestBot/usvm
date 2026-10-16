package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.UPathSelector
import org.usvm.machine.TsEntryPointTimeQuantumEvent
import org.usvm.machine.TsSolverQueryEvent
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Serializable
internal data class CallsClassRawIrPoint(
    val method: String,
    val statementIndex: Int,
    val sourceFile: String,
    val startOffset: Int,
    val endOffset: Int,
    val nodeKind: String,
)

private fun EtsStmt.toClassRawIrPoint(sourceFile: String): CallsClassRawIrPoint {
    val origin = checkNotNull(location.origin)
    return CallsClassRawIrPoint(
        method = location.method.signature.toString(),
        statementIndex = location.index,
        sourceFile = sourceFile,
        startOffset = origin.startOffset,
        endOffset = origin.endOffset,
        nodeKind = origin.nodeKind,
    )
}

@Serializable
internal data class CallsClassRawStateSnapshot(
    val stateId: String,
    val outcome: String,
    val entryMethod: String,
    val currentMethod: String,
    val statementIndex: Int,
    val statement: String,
    val callStack: List<String>,
    val constraintIds: List<Long>,
    val modelCount: Int,
    val resultKind: String,
    val symbolicResultId: Long? = null,
    val unsupportedReason: String? = null,
    val engineFailureReason: String? = null,
)

@Serializable
internal data class CallsClassRawAnalysis(
    val budgetMillis: Long,
    val analysisElapsedMillis: Long,
    val budgetOverrunMillis: Long,
    val timingSchemaVersion: Int = 0,
    val analysisSetupElapsedMillis: Long = 0,
    val searchElapsedMillis: Long = 0,
    val remainingStateDrainMillis: Long = 0,
    /** Snapshot work overlaps search or drain; it is not an additional phase. */
    val searchSnapshotElapsedNanos: Long = 0,
    val drainSnapshotElapsedNanos: Long = 0,
    val journalFinalizeMillis: Long = 0,
    val timeQuantumMillis: Long,
    val collectedStatesLimit: Int? = null,
    val profile: CallsExperimentProfile,
    val seed: Long,
    val returnedStates: Long,
    val exceptionalStates: Long,
    val unsupportedTerminalStates: Long,
    val otherReachableTerminalStates: Long,
    val unreachableTerminalStates: Long,
    val stoppedPrefixStates: Long,
    val remainingStates: Long,
    val stateSnapshotFailures: List<String>,
    val solverCalls: Long,
    val solverElapsedNanos: Long,
    val solverStatusCounts: Map<String, Long>,
    val timeQuantumCount: Long,
    val timeQuantumOverruns: Long,
    val methodSteps: Map<String, Long>,
    val sourceEnteredStatementKinds: Map<String, Long>,
    val sourceMappedIrUniverse: List<CallsClassRawIrPoint>,
    val sourceMappedIrEntered: List<CallsClassRawIrPoint>,
    val statementUniverseSha256: String,
    val stateSnapshots: CallsClassRawJournalMetadata? = null,
    val symbolicExpressions: CallsClassRawJournalMetadata? = null,
    val inputCandidates: CallsClassInputJournalMetadata? = null,
    val inputExtractionFailures: CallsClassInputJournalMetadata? = null,
    val candidateExtractionElapsedNanos: Long = 0,
    val candidateWriteElapsedNanos: Long = 0,
    val searchCandidateElapsedNanos: Long = 0,
    val drainCandidateElapsedNanos: Long = 0,
    val rawStateDiagnosticsEnabled: Boolean = false,
    val engineFailureStates: Long = 0L,
    val proof: String = "Symbolic source-mapped IR entry only; " +
        "concrete input candidates are not native replay coverage; environment completeness is explicit",
)

/** Search metrics survive an interrupted frontier materialization; this is explicitly a partial result. */
@Serializable
internal data class CallsClassSearchCheckpoint(
    val stopReason: String?,
    val searchElapsedMillis: Long,
    val returnedStates: Long,
    val exceptionalStates: Long,
    val unsupportedTerminalStates: Long,
    val engineFailureStates: Long,
    val stoppedPrefixStates: Long,
    val sourceMappedIrUniverse: List<CallsClassRawIrPoint>,
    val sourceMappedIrEntered: List<CallsClassRawIrPoint>,
    val inputCandidatesPath: String = "input-candidates.jsonl",
    val inputExtractionFailuresPath: String = "input-extraction-failures.jsonl",
    val remainingStateDrainComplete: Boolean = false,
)

/** Retains raw symbolic outcomes without requiring finite native observation domains. */
internal class ClassRawAnalysisObserver(
    private val sourceFile: String,
    universe: List<CallsClassRawIrPoint>,
    private val output: Path,
    private val retainRawStateDiagnostics: Boolean,
    private val profile: CallsExperimentProfile,
) : UMachineObserver<TsState> {
    private val universe = universe.toList()
    private val universePoints = this.universe.toSet()
    private val universeViolations = linkedSetOf<CallsClassRawIrPoint>()

    init {
        require(universePoints.size == this.universe.size) { "Class IR universe contains duplicate points" }
    }

    private val entered = linkedSetOf<CallsClassRawIrPoint>()
    private val snapshots = if (retainRawStateDiagnostics) CallsClassRawSnapshotStore(output = output) else null
    private val candidates = CallsClassInputCandidates(output = output, profile = profile)
    private val snapshotFailures = mutableListOf<String>()
    private val methodSteps = linkedMapOf<String, Long>()
    private val sourceKinds = linkedMapOf<String, Long>()
    private val solverStatuses = linkedMapOf<String, Long>()
    private var executingMethod: String? = null
    private var returnedStates = 0L
    private var exceptionalStates = 0L
    private var unsupportedStates = 0L
    private var engineFailureStates = 0L
    private var otherStates = 0L
    private var unreachableStates = 0L
    private var stoppedStates = 0L
    private var remainingStates = 0L
    private var solverCalls = 0L
    private var solverNanos = 0L
    private var quantumCount = 0L
    private var quantumOverruns = 0L
    private var searchStarted: TimeMark? = null
    private var searchElapsedMillis = 0L
    private var draining = false
    private var remainingStateDrainMillis = 0L
    private var searchSnapshotNanos = 0L
    private var drainSnapshotNanos = 0L
    private var searchCandidateNanos = 0L
    private var drainCandidateNanos = 0L

    fun registerInitialState(method: EtsMethod, state: TsState) {
        candidates.registerInitialState(method = method, state = state)
    }

    override fun onMachineStarted() {
        searchStarted = TimeSource.Monotonic.markNow()
    }

    override fun onMachineStopped() {
        searchElapsedMillis = searchStarted?.elapsedNow()?.inWholeMilliseconds ?: 0
    }

    fun recordOriginalEntry(stmt: EtsStmt) {
        val point = stmt.toClassRawIrPoint(sourceFile = sourceFile)
        if (point !in universePoints) {
            universeViolations += point
            error("Original class IR entry is outside its frozen universe: $point")
        }

        entered += point
        sourceKinds.merge(stmt::class.java.simpleName, 1L, Long::plus)
    }

    override fun onStatePeeked(state: TsState) {
        val method = state.lastStmt.location.method
        executingMethod = method.signature.toString()
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        executingMethod?.let { methodSteps.merge(it, 1L, Long::plus) }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        if (state.engineFailureReason != null) {
            engineFailureStates++
            saveSnapshot(state = state, outcome = "ENGINE_FAILURE")
            return
        }

        if (!stateReachable) {
            unreachableStates++
            return
        }
        val outcome = when {
            state.unsupportedReason != null -> {
                unsupportedStates++
                "UNSUPPORTED"
            }
            state.methodResult is TsMethodResult.TsException -> {
                exceptionalStates++
                "THREW"
            }
            state.methodResult is TsMethodResult.Success -> {
                returnedStates++
                "RETURNED"
            }
            else -> {
                otherStates++
                "OTHER_REACHABLE"
            }
        }
        saveSnapshot(state = state, outcome = outcome)
    }

    fun recordStoppedPrefix(state: TsState, reason: String) {
        stoppedStates++
        saveSnapshot(state = state, outcome = "STOPPED_PREFIX: $reason")
    }

    fun checkpointBeforeDrain(stopReason: String?) {
        candidates.checkpoint()
        val checkpoint = CallsClassSearchCheckpoint(
            stopReason = stopReason,
            searchElapsedMillis = searchElapsedMillis,
            returnedStates = returnedStates,
            exceptionalStates = exceptionalStates,
            unsupportedTerminalStates = unsupportedStates,
            engineFailureStates = engineFailureStates,
            stoppedPrefixStates = stoppedStates,
            sourceMappedIrUniverse = universe,
            sourceMappedIrEntered = entered.toList(),
        )
        writeCallsArtifactAtomically(output.resolve("search-checkpoint.json"), encodeCallsArtifact(checkpoint))
    }

    /** Drain only after analyzeWithMetadata has computed its real stop reason. */
    fun retainRemainingStates(selector: UPathSelector<TsState>?) {
        val started = TimeSource.Monotonic.markNow()
        draining = true
        try {
            selector?.drain()?.forEach { state ->
                remainingStates++
                saveSnapshot(state = state, outcome = "SUSPENDED")
            }
        } finally {
            remainingStateDrainMillis = started.elapsedNow().inWholeMilliseconds
            draining = false
        }
    }

    fun recordSolverQuery(event: TsSolverQueryEvent) {
        solverCalls++
        solverNanos += event.elapsedNanos
        solverStatuses.merge(event.status?.name ?: "ERROR", 1L, Long::plus)
    }

    fun recordTimeQuantum(event: TsEntryPointTimeQuantumEvent) {
        quantumCount++
        if (event.elapsed > event.requested) quantumOverruns++
    }

    private fun saveSnapshot(state: TsState, outcome: String) {
        val candidateStarted = TimeSource.Monotonic.markNow()
        val candidateWrite = runCatching {
            candidates.append(
                state = state,
                outcome = outcome,
                emittedAtMillis = searchStarted?.elapsedNow()?.inWholeMilliseconds ?: 0,
                stopped = draining,
            )
        }
        candidateWrite.exceptionOrNull()?.let { failure ->
            snapshotFailures += "Concrete candidate write failed: ${failure::class.java.name}: ${failure.message}"
        }
        val candidateNanos = candidateStarted.elapsedNow().inWholeNanoseconds
        if (draining) drainCandidateNanos += candidateNanos else searchCandidateNanos += candidateNanos

        val rawSnapshots = snapshots ?: return
        saveRawSnapshot(store = rawSnapshots, state = state, outcome = outcome)
    }

    private fun saveRawSnapshot(store: CallsClassRawSnapshotStore, state: TsState, outcome: String) {
        val started = TimeSource.Monotonic.markNow()
        val saved = runCatching {
            store.append { reference ->
                val stmt = state.lastStmt
                val result = state.methodResult
                CallsClassRawStateSnapshot(
                    stateId = state.id.toString(),
                    outcome = outcome,
                    entryMethod = state.entrypoint.signature.toString(),
                    currentMethod = stmt.location.method.signature.toString(),
                    statementIndex = stmt.location.index,
                    statement = stmt.toString(),
                    callStack = state.callStack.map { it.method.signature.toString() },
                    constraintIds = state.pathConstraints.constraintSequence().map(reference).toList(),
                    modelCount = state.models.size,
                    resultKind = result::class.java.simpleName,
                    symbolicResultId = when (result) {
                        is TsMethodResult.Success -> reference(result.value)
                        is TsMethodResult.TsException -> reference(result.value)
                        else -> null
                    },
                    unsupportedReason = state.unsupportedReason,
                    engineFailureReason = state.engineFailureReason,
                )
            }
        }
        saved.exceptionOrNull()?.let { failure ->
            val diagnostic = "${state.id}/$outcome: ${failure::class.java.name}: ${failure.message}"
            snapshotFailures += diagnostic
            // A journal failure must remain visible even when this diagnostic write also fails.
            runCatching {
                Files.writeString(
                    output.resolve("state-snapshot-failures.jsonl"),
                    encodeCallsArtifact(mapOf("failure" to diagnostic)) + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
            }
        }
        val elapsedNanos = started.elapsedNow().inWholeNanoseconds
        if (draining) {
            drainSnapshotNanos += elapsedNanos
        } else {
            searchSnapshotNanos += elapsedNanos
        }
    }

    fun result(manifest: CallsClassStartManifest, analysisElapsedMillis: Long): CallsClassRawAnalysis {
        val encodedUniverse = encodeCallsArtifact(universe).toByteArray(Charsets.UTF_8)
        val finalizeStarted = TimeSource.Monotonic.markNow()
        val journals = snapshots?.finish()
        val inputs = candidates.finish()
        val journalFinalizeMillis = finalizeStarted.elapsedNow().inWholeMilliseconds

        // Exploratory execution may retain interpreter failures as terminal states.
        // Such a failure must still invalidate this diagnostic denominator.
        check(universeViolations.isEmpty()) {
            "Original class IR entries are outside their frozen universe: $universeViolations"
        }

        return CallsClassRawAnalysis(
            budgetMillis = manifest.budgetMillis,
            analysisElapsedMillis = analysisElapsedMillis,
            budgetOverrunMillis = (searchElapsedMillis - manifest.budgetMillis).coerceAtLeast(0),
            timingSchemaVersion = 2,
            analysisSetupElapsedMillis = (analysisElapsedMillis - searchElapsedMillis).coerceAtLeast(0),
            searchElapsedMillis = searchElapsedMillis,
            remainingStateDrainMillis = remainingStateDrainMillis,
            searchSnapshotElapsedNanos = searchSnapshotNanos,
            drainSnapshotElapsedNanos = drainSnapshotNanos,
            journalFinalizeMillis = journalFinalizeMillis,
            timeQuantumMillis = manifest.timeQuantumMillis,
            profile = manifest.profile,
            seed = manifest.seed,
            collectedStatesLimit = manifest.collectedStatesLimit,
            returnedStates = returnedStates,
            exceptionalStates = exceptionalStates,
            unsupportedTerminalStates = unsupportedStates,
            otherReachableTerminalStates = otherStates,
            unreachableTerminalStates = unreachableStates,
            stoppedPrefixStates = stoppedStates,
            remainingStates = remainingStates,
            stateSnapshotFailures = snapshotFailures,
            solverCalls = solverCalls,
            solverElapsedNanos = solverNanos,
            solverStatusCounts = solverStatuses,
            timeQuantumCount = quantumCount,
            timeQuantumOverruns = quantumOverruns,
            methodSteps = methodSteps,
            sourceEnteredStatementKinds = sourceKinds,
            sourceMappedIrUniverse = universe,
            sourceMappedIrEntered = entered.toList(),
            statementUniverseSha256 = callsArtifactSha256(encodedUniverse),
            stateSnapshots = journals?.first,
            symbolicExpressions = journals?.second,
            inputCandidates = inputs.first,
            inputExtractionFailures = inputs.second,
            candidateExtractionElapsedNanos = candidates.extractionElapsedNanos,
            candidateWriteElapsedNanos = candidates.writeElapsedNanos,
            searchCandidateElapsedNanos = searchCandidateNanos,
            drainCandidateElapsedNanos = drainCandidateNanos,
            rawStateDiagnosticsEnabled = retainRawStateDiagnostics,
            engineFailureStates = engineFailureStates,
        )
    }

    companion object {
        fun forTarget(
            methods: List<EtsMethod>,
            sourceFile: String,
            sourceText: String,
            output: Path,
            retainRawStateDiagnostics: Boolean,
            profile: CallsExperimentProfile,
        ): ClassRawAnalysisObserver {
            val target = methods.first().signature.enclosingClass
            val universe = methods.flatMap { it.cfg.stmts }.filter {
                isOriginalClassStartupStep(stmt = it, target = target, sourceFile = sourceFile, sourceText = sourceText)
            }.map { it.toClassRawIrPoint(sourceFile = sourceFile) }
            return ClassRawAnalysisObserver(
                sourceFile = sourceFile,
                universe = universe,
                output = output,
                retainRawStateDiagnostics = retainRawStateDiagnostics,
                profile = profile,
            )
        }
    }
}
