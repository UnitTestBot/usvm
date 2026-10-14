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
    val constraints: List<String>,
    val modelCount: Int,
    val resultKind: String,
    val symbolicResult: String? = null,
    val unsupportedReason: String? = null,
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
    val sourceMappedIrUniverse: List<CallsClassRawIrPoint>,
    val sourceMappedIrEntered: List<CallsClassRawIrPoint>,
    val statementUniverseSha256: String,
    val terminalStateSnapshots: List<CallsClassRawStateSnapshot>,
    val stoppedPrefixSnapshots: List<CallsClassRawStateSnapshot>,
    val remainingStateSnapshots: List<CallsClassRawStateSnapshot>,
    val proof: String = "Symbolic source-mapped IR entry only; " +
        "diagnostic state metadata is not a native replay witness",
)

/** Retains raw symbolic outcomes without requiring finite native observation domains. */
internal class ClassRawAnalysisObserver(
    private val sourceFile: String,
    private val universe: List<CallsClassRawIrPoint>,
    private val output: Path,
) : UMachineObserver<TsState> {
    private val entered = linkedSetOf<CallsClassRawIrPoint>()
    private val terminal = mutableListOf<CallsClassRawStateSnapshot>()
    private val stopped = mutableListOf<CallsClassRawStateSnapshot>()
    private val remaining = mutableListOf<CallsClassRawStateSnapshot>()
    private val snapshotFailures = mutableListOf<String>()
    private val methodSteps = linkedMapOf<String, Long>()
    private val solverStatuses = linkedMapOf<String, Long>()
    private var executingMethod: String? = null
    private var returnedStates = 0L
    private var exceptionalStates = 0L
    private var unsupportedStates = 0L
    private var otherStates = 0L
    private var unreachableStates = 0L
    private var stoppedStates = 0L
    private var remainingStates = 0L
    private var solverCalls = 0L
    private var solverNanos = 0L
    private var quantumCount = 0L
    private var quantumOverruns = 0L

    fun recordOriginalEntry(stmt: EtsStmt) {
        entered += stmt.toClassRawIrPoint(sourceFile = sourceFile)
    }

    override fun onStatePeeked(state: TsState) {
        val method = state.lastStmt.location.method
        executingMethod = method.signature.toString()
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        executingMethod?.let { methodSteps.merge(it, 1L, Long::plus) }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
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
        saveSnapshot(state = state, outcome = outcome, destination = terminal)
    }

    fun recordStoppedPrefix(state: TsState, reason: String) {
        stoppedStates++
        saveSnapshot(state = state, outcome = "STOPPED_PREFIX: $reason", destination = stopped)
    }

    /** Drain only after analyzeWithMetadata has computed its real stop reason. */
    fun retainRemainingStates(selector: UPathSelector<TsState>?) {
        selector?.drain()?.forEach { state ->
            remainingStates++
            saveSnapshot(state = state, outcome = "SUSPENDED", destination = remaining)
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

    private fun saveSnapshot(state: TsState, outcome: String, destination: MutableList<CallsClassRawStateSnapshot>) {
        val snapshot = runCatching {
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
                constraints = state.pathConstraints.constraintSequence().map { it.toString() }.toList(),
                modelCount = state.models.size,
                resultKind = result::class.java.simpleName,
                symbolicResult = when (result) {
                    is TsMethodResult.Success -> result.value.toString()
                    is TsMethodResult.TsException -> result.value.toString()
                    else -> null
                },
                unsupportedReason = state.unsupportedReason,
            )
        }
        snapshot.fold(
            onSuccess = {
                destination += it
                val saved = runCatching {
                    Files.writeString(
                        output.resolve("states.jsonl"),
                        encodeCallsArtifact(it) + "\n",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                    )
                }
                saved.exceptionOrNull()?.let { failure ->
                    snapshotFailures += "${state.id}/$outcome: ${failure::class.java.name}: ${failure.message}"
                }
            },
            onFailure = {
                val diagnostic = "${state.id}/$outcome: ${it::class.java.name}: ${it.message}"
                snapshotFailures += diagnostic
                Files.writeString(
                    output.resolve("state-snapshot-failures.jsonl"),
                    encodeCallsArtifact(mapOf("failure" to diagnostic)) + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
            },
        )
    }

    fun result(manifest: CallsClassStartManifest, analysisElapsedMillis: Long): CallsClassRawAnalysis {
        val encodedUniverse = encodeCallsArtifact(universe).toByteArray(Charsets.UTF_8)
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
            sourceMappedIrUniverse = universe,
            sourceMappedIrEntered = entered.toList(),
            statementUniverseSha256 = callsArtifactSha256(encodedUniverse),
            terminalStateSnapshots = terminal,
            stoppedPrefixSnapshots = stopped,
            remainingStateSnapshots = remaining,
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
