package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.interpreter.TsInitialStateSolverUnknownException
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import kotlin.time.Duration
import kotlin.time.TimeSource

@Serializable
internal data class CallsCoverageCandidate(
    val emittedAtMillis: Long,
    val emittedAtStep: Long,
    val inputs: List<JsConcreteValue>,
    val newSymbolicStatements: Int,
    val completion: CallsCoverageCompletion,
)

@Serializable
internal enum class CallsCoverageCompletion {
    RETURNED,
    THREW,
}

@Serializable
internal enum class CallsCoverageSearchStatus {
    EXHAUSTED,
    TIMEOUT,
    STOPPED,
    UNSUPPORTED,
    UNMAPPED,
    AMBIGUOUS,
    UNKNOWN,
    TOOL_ERROR,
}

@Serializable
internal data class CallsCoverageSearchResult(
    val status: CallsCoverageSearchStatus,
    val candidates: List<CallsCoverageCandidate>,
    val selectedStates: Int,
    val executedSteps: Long,
    val stepsWithinBudget: Long,
    val extractionFailures: List<String>,
    val candidateCapReached: Boolean,
    val preparationElapsedMillis: Long,
    val machineSetupElapsedMillis: Long,
    val searchElapsedMillis: Long,
    val machineTeardownElapsedMillis: Long,
    val unsupportedCall: Boolean = false,
    val engineFailed: Boolean = false,
    val runtimeLimited: Boolean = false,
    val runtimeLimitationReasons: List<String> = emptyList(),
    val diagnostic: String? = null,
)

internal data class CallsCoverageSearchRequest(
    val sourceRoot: java.nio.file.Path,
    val project: CallsProjectCase,
    val function: CallsFunctionCase,
    val profile: CallsExperimentProfile,
    val frozenModelIds: Set<String>,
    val expectedNativeFrontendRevision: String,
    val seed: Long,
    val budget: Duration,
    val solverQueryLimit: Duration,
    val solverType: SolverType = SolverType.YICES,
    val candidateCap: Int,
    val onCandidate: (CallsCoverageCandidate) -> Unit = {},
) {
    init {
        require(budget > Duration.ZERO)
        require(solverQueryLimit > Duration.ZERO && solverQueryLimit <= budget)
        require(candidateCap > 0)
    }
}

/** Runs one function without a return target and records completed coverage-novel paths. */
internal class CurrentTsCallsCoverageEngine(
    private val sourceEngine: CurrentTsCallsSymbolicEngine = CurrentTsCallsSymbolicEngine(),
) {
    fun search(request: CallsCoverageSearchRequest): CallsCoverageSearchResult {
        val preparationStarted = TimeSource.Monotonic.markNow()
        val preparation = sourceEngine.prepareFunction(
            CallsFunctionPreflightRequest(
                sourceRoot = request.sourceRoot,
                project = request.project,
                function = request.function,
                expectedNativeFrontendRevision = request.expectedNativeFrontendRevision,
            ),
        )
        val preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds
        if (preparation is CallsFunctionPreparation.Rejected) {
            return preparation.coverageResult(preparationElapsedMillis = preparationElapsedMillis)
        }

        preparation as CallsFunctionPreparation.Eligible
        val symbolicInputs = CallsSymbolicInputs(
            inputs = request.function.inputs,
            bindings = preparation.inputBindings,
            lexicalEnvironment = preparation.lexicalEnvironment,
            captures = preparation.captures,
            sourceScene = preparation.scene,
        )
        val candidates = mutableListOf<CallsCoverageCandidate>()
        val extractionFailures = mutableListOf<String>()
        val machineCallStarted = TimeSource.Monotonic.markNow()
        val observer = CompletedCoverageCandidateObserver(
            method = preparation.method,
            inputDomains = request.function.inputs,
            symbolicInputs = symbolicInputs,
            candidateCap = request.candidateCap,
            budgetMillis = request.budget.inWholeMilliseconds,
            machineCallStarted = machineCallStarted,
            onCandidate = { candidate ->
                request.onCandidate(candidate)
                candidates += candidate
            },
            onExtractionFailure = extractionFailures::add,
        )
        val modelSelection = if (request.profile.usesFrozenModels) {
            TsUnknownCallModelSelection.Only(request.frozenModelIds)
        } else {
            TsUnknownCallModelSelection.Only(emptySet())
        }
        val runtimeLimitationReasons = linkedSetOf<String>()
        val interpreterObserver = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                runtimeLimitationReasons += event.reason.name
            }
        }
        val inputCapacity = request.function.inputs.maxOfOrNull { it.domain.callsInputCapacity() } ?: 0
        val machineOptions = coverageMachineOptions(request)
        val machineResult = runCatching {
            TsMachine(
                scene = symbolicInputs.scene,
                options = machineOptions,
                tsOptions = TsOptions(
                    maxArraySize = maxOf(TsOptions().maxArraySize, inputCapacity),
                    unknownCallModelSelection = modelSelection,
                    unknownCallFallback = request.profile.fallback,
                ),
                machineObserver = observer,
                observer = interpreterObserver,
            ).use { machine ->
                machine.analyzeWithMetadata(
                    methods = listOf(preparation.method),
                    configureInitialState = { _, state -> symbolicInputs.initialize(state) },
                )
            }
        }
        val machineCallElapsedMillis = machineCallStarted.elapsedNow().inWholeMilliseconds
        val machineTeardownElapsedMillis = (
            machineCallElapsedMillis - observer.machineSetupElapsedMillis - observer.searchElapsedMillis
            ).coerceAtLeast(0)
        val outcome = machineResult.getOrNull()
        val status = when {
            machineResult.exceptionOrNull() is TsInitialStateSolverUnknownException -> CallsCoverageSearchStatus.UNKNOWN
            machineResult.isFailure -> CallsCoverageSearchStatus.TOOL_ERROR
            outcome?.timedOut == true -> CallsCoverageSearchStatus.TIMEOUT
            outcome?.stopReason == TsAnalysisStopReason.EXHAUSTED -> CallsCoverageSearchStatus.EXHAUSTED
            else -> CallsCoverageSearchStatus.STOPPED
        }

        return CallsCoverageSearchResult(
            status = status,
            candidates = candidates,
            selectedStates = observer.selectedStates,
            executedSteps = observer.executedSteps,
            stepsWithinBudget = observer.stepsWithinBudget,
            extractionFailures = extractionFailures,
            candidateCapReached = observer.capReached,
            preparationElapsedMillis = preparationElapsedMillis,
            machineSetupElapsedMillis = observer.machineSetupElapsedMillis,
            searchElapsedMillis = observer.searchElapsedMillis,
            machineTeardownElapsedMillis = machineTeardownElapsedMillis,
            unsupportedCall = outcome?.unsupportedCall == true,
            engineFailed = outcome?.engineFailed == true,
            runtimeLimited = outcome?.runtimeLimited == true || runtimeLimitationReasons.isNotEmpty(),
            runtimeLimitationReasons = runtimeLimitationReasons.toList(),
            diagnostic = machineResult.exceptionOrNull()?.let { error -> error.message ?: error::class.java.name },
        )
    }

    private fun coverageMachineOptions(request: CallsCoverageSearchRequest): UMachineOptions =
        UMachineOptions(
            pathSelectionStrategies = listOf(CALLS_PATH_SELECTION_STRATEGY),
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            randomSeed = request.seed,
            timeout = request.budget,
            solverTimeout = request.solverQueryLimit,
            solverType = request.solverType,
            stopOnCoverage = CALLS_STOP_ON_COVERAGE,
            stopOnTargetsReached = false,
            throwExceptionOnStepFailure = true,
        )
}

private fun CallsFunctionPreparation.Rejected.coverageResult(
    preparationElapsedMillis: Long,
): CallsCoverageSearchResult {
    return CallsCoverageSearchResult(
        status = status.toCoverageStatus(),
        candidates = emptyList(),
        selectedStates = 0,
        executedSteps = 0,
        stepsWithinBudget = 0,
        extractionFailures = emptyList(),
        candidateCapReached = false,
        preparationElapsedMillis = preparationElapsedMillis,
        machineSetupElapsedMillis = 0,
        searchElapsedMillis = 0,
        machineTeardownElapsedMillis = 0,
        diagnostic = "$reasonCode: $diagnostic",
    )
}

private class CompletedCoverageCandidateObserver(
    private val method: EtsMethod,
    private val inputDomains: List<org.usvm.ts.pbt.model.PropertyInput>,
    private val symbolicInputs: CallsSymbolicInputs,
    private val candidateCap: Int,
    private val budgetMillis: Long,
    private val machineCallStarted: TimeSource.Monotonic.ValueTimeMark,
    private val onCandidate: (CallsCoverageCandidate) -> Unit,
    private val onExtractionFailure: (String) -> Unit,
) : UMachineObserver<TsState> {
    private val entryStatements: Set<EtsStmt> = method.cfg.stmts.toSet()
    private val coveredStatements = hashSetOf<EtsStmt>()
    private val inputKeys = hashSetOf<String>()
    private var machineStarted: TimeSource.Monotonic.ValueTimeMark? = null

    var selectedStates: Int = 0
        private set

    var capReached: Boolean = false
        private set

    var executedSteps: Long = 0
        private set

    var stepsWithinBudget: Long = 0
        private set

    var machineSetupElapsedMillis: Long = 0
        private set

    var searchElapsedMillis: Long = 0
        private set

    override fun onMachineStarted() {
        machineSetupElapsedMillis = machineCallStarted.elapsedNow().inWholeMilliseconds
        machineStarted = TimeSource.Monotonic.markNow()
    }

    override fun onMachineStopped() {
        searchElapsedMillis = elapsedSinceMachineStart()
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        executedSteps++
        if (elapsedSinceMachineStart() <= budgetMillis) {
            stepsWithinBudget++
        }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        if (!stateReachable) return

        val completion = when (val result = state.methodResult) {
            is TsMethodResult.Success.RegularCall -> {
                if (result.method != method) return
                CallsCoverageCompletion.RETURNED
            }
            is TsMethodResult.TsException -> {
                CallsCoverageCompletion.THREW
            }
            else -> {
                return
            }
        }
        val pathStatements = state.pathNode.allStatements.filterTo(hashSetOf()) { statement ->
            statement in entryStatements
        }
        val newStatements = pathStatements.count { statement -> statement !in coveredStatements }
        if (newStatements == 0) return

        if (selectedStates >= candidateCap) {
            capReached = true
            return
        }
        selectedStates++
        val candidate = runCatching {
            val inputs = symbolicInputs.resolve(state).firstOrNull { candidate ->
                candidate.size == inputDomains.size &&
                    candidate.zip(inputDomains).all { (value, input) -> value in input.domain }
            }
            requireNotNull(inputs) { "No extracted input is inside every frozen domain" }
            val key = CallsExperimentJson.json.encodeToUtf8SafeString(inputs)
            if (inputKeys.add(key)) {
                CallsCoverageCandidate(
                    emittedAtMillis = elapsedSinceMachineStart(),
                    emittedAtStep = executedSteps,
                    inputs = inputs,
                    newSymbolicStatements = newStatements,
                    completion = completion,
                )
            } else {
                null
            }
        }.getOrElse { error ->
            onExtractionFailure(error.message ?: error::class.java.name)
            null
        }

        if (candidate != null) {
            onCandidate(candidate)
            coveredStatements += pathStatements
        }
    }

    private fun elapsedSinceMachineStart(): Long = requireNotNull(machineStarted) {
        "Coverage machine clock has not started"
    }.elapsedNow().inWholeMilliseconds
}

private fun CallsSymbolicStatus.toCoverageStatus(): CallsCoverageSearchStatus = when (this) {
    CallsSymbolicStatus.UNSUPPORTED, CallsSymbolicStatus.RUNTIME_LIMITATION -> CallsCoverageSearchStatus.UNSUPPORTED
    CallsSymbolicStatus.UNMAPPED -> CallsCoverageSearchStatus.UNMAPPED
    CallsSymbolicStatus.AMBIGUOUS -> CallsCoverageSearchStatus.AMBIGUOUS
    else -> CallsCoverageSearchStatus.TOOL_ERROR
}
