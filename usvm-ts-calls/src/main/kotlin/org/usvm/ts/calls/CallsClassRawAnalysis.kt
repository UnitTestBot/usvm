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
    val stateSnapshots: CallsClassRawJournalMetadata,
    val symbolicExpressions: CallsClassRawJournalMetadata,
    val engineFailureStates: Long = 0L,
    val proof: String = "Symbolic source-mapped IR entry only; " +
        "diagnostic state metadata is not a native replay witness",
)

/** Retains raw symbolic outcomes without requiring finite native observation domains. */
internal class ClassRawAnalysisObserver(
    private val sourceFile: String,
    universe: List<CallsClassRawIrPoint>,
    private val output: Path,
) : UMachineObserver<TsState> {
    private val universe = universe.toList()
    private val universePoints = this.universe.toSet()
    private val universeViolations = linkedSetOf<CallsClassRawIrPoint>()

    init {
        require(universePoints.size == this.universe.size) { "Class IR universe contains duplicate points" }
    }

    private val entered = linkedSetOf<CallsClassRawIrPoint>()
    private val snapshots = CallsClassRawSnapshotStore(output = output)
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

    /** Drain only after analyzeWithMetadata has computed its real stop reason. */
    fun retainRemainingStates(selector: UPathSelector<TsState>?) {
        selector?.drain()?.forEach { state ->
            remainingStates++
            saveSnapshot(state = state, outcome = "SUSPENDED")
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
        val saved = runCatching {
            snapshots.append { reference ->
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
    }

    fun result(manifest: CallsClassStartManifest, analysisElapsedMillis: Long): CallsClassRawAnalysis {
        val encodedUniverse = encodeCallsArtifact(universe).toByteArray(Charsets.UTF_8)
        val journals = snapshots.finish()
        // Exploratory execution may retain interpreter failures as terminal states.
        // Such a failure must still invalidate this diagnostic denominator.
        check(universeViolations.isEmpty()) {
            "Original class IR entries are outside their frozen universe: $universeViolations"
        }

        return CallsClassRawAnalysis(
            budgetMillis = manifest.budgetMillis,
            analysisElapsedMillis = analysisElapsedMillis,
            budgetOverrunMillis = (analysisElapsedMillis - manifest.budgetMillis).coerceAtLeast(0),
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
            stateSnapshots = journals.first,
            symbolicExpressions = journals.second,
            engineFailureStates = engineFailureStates,
        )
    }

    companion object {
        fun forTarget(
            methods: List<EtsMethod>,
            sourceFile: String,
            sourceText: String,
            output: Path,
        ): ClassRawAnalysisObserver {
            val target = methods.first().signature.enclosingClass
            val universe = methods.flatMap { it.cfg.stmts }.filter {
                isOriginalClassStartupStep(stmt = it, target = target, sourceFile = sourceFile, sourceText = sourceText)
            }.map { it.toClassRawIrPoint(sourceFile = sourceFile) }
            return ClassRawAnalysisObserver(sourceFile = sourceFile, universe = universe, output = output)
        }
    }
}
