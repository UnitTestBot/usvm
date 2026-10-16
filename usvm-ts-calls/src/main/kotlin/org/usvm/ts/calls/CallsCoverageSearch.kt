package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.CoverageZone
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
    val completion: CallsCoverageCompletion?,
    val entryPointId: String? = null,
    val observation: CallsClassObservation? = null,
    val origin: CallsCoverageCandidateOrigin = CallsCoverageCandidateOrigin.TERMINATED,
    val suspendedAtMachineStop: Boolean = false,
    val suspensionReason: String? = null,
) {
    init {
        when (origin) {
            CallsCoverageCandidateOrigin.TERMINATED -> require(
                completion != null && !suspendedAtMachineStop && suspensionReason == null,
            )
            CallsCoverageCandidateOrigin.SUSPENDED -> require(completion == null && observation == null) {
                "Suspended candidates carry inputs only, without a symbolic completion or observation"
            }
        }
    }
}

@Serializable
internal enum class CallsCoverageCandidateOrigin { TERMINATED, SUSPENDED }

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
    val suspendedStates: Int = 0,
    val suspendedExtractionElapsedMillis: Long = 0,
    val frontierFlushElapsedMillis: Long = 0,
    val machineStopReason: String? = null,
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

/** Runs one function and records completed paths adding coverage in its declared symbolic scope. */
internal class CurrentTsCallsCoverageEngine(
    private val sourceEngine: CurrentTsCallsSymbolicEngine = CurrentTsCallsSymbolicEngine(),
) {
    @Suppress("LongMethod")
    fun search(request: CallsCoverageSearchRequest): CallsCoverageSearchResult {
        val preparationStarted = TimeSource.Monotonic.markNow()
        val preparation = sourceEngine.prepareFunction(request.toFunctionPreflightRequest())
        if (preparation is CallsFunctionPreparation.Rejected) {
            return preparation.coverageResult(
                preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds,
            )
        }

        preparation as CallsFunctionPreparation.Eligible
        val scope = resolveCallsSymbolicCoverageScope(preparation, target = request.function.symbolicCoverageClass)
        val preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds
        if (scope is CallsSymbolicCoverageScope.Rejected) {
            return emptyCoverageResult(scope.status, preparationElapsedMillis, scope.diagnostic)
        }
        scope as CallsSymbolicCoverageScope.Selected
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
            contexts = listOf(
                CallsCoverageCandidateContext(
                    method = preparation.method,
                    inputDomains = request.function.inputs,
                    symbolicInputs = symbolicInputs,
                ),
            ),
            coverageStatements = scope.statements,
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
        val machineOptions = coverageMachineOptions(request, scope)
        val machineResult = runCatching {
            TsMachine(
                scene = symbolicInputs.scene,
                options = machineOptions,
                tsOptions = TsOptions(
                    maxArraySize = maxOf(TsOptions().maxArraySize, inputCapacity),
                    unknownCallModelSelection = modelSelection,
                    unknownCallFallback = request.profile.fallback,
                    classCoverageTargets = scope.classSignature?.let { signature -> setOf(signature) },
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
        observer.finishTiming()
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

    private fun coverageMachineOptions(
        request: CallsCoverageSearchRequest,
        scope: CallsSymbolicCoverageScope.Selected,
    ): UMachineOptions =
        UMachineOptions(
            pathSelectionStrategies = listOf(CALLS_PATH_SELECTION_STRATEGY),
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            randomSeed = request.seed,
            timeout = request.budget,
            solverTimeout = request.solverQueryLimit,
            solverType = request.solverType,
            stopOnCoverage = CALLS_STOP_ON_COVERAGE,
            coverageZone = if (scope.classSignature == null) CoverageZone.METHOD else CoverageZone.CLASS,
            removeCoveredEntryPoints = scope.classSignature == null,
            stopOnTargetsReached = false,
            throwExceptionOnStepFailure = true,
        )
}

private fun CallsCoverageSearchRequest.toFunctionPreflightRequest(): CallsFunctionPreflightRequest =
    CallsFunctionPreflightRequest(
        sourceRoot = sourceRoot,
        project = project,
        function = function,
        expectedNativeFrontendRevision = expectedNativeFrontendRevision,
    )

private fun CallsFunctionPreparation.Rejected.coverageResult(
    preparationElapsedMillis: Long,
): CallsCoverageSearchResult = emptyCoverageResult(
    status = status.toCoverageStatus(),
    preparationElapsedMillis = preparationElapsedMillis,
    diagnostic = "$reasonCode: $diagnostic",
)

private fun emptyCoverageResult(
    status: CallsCoverageSearchStatus,
    preparationElapsedMillis: Long,
    diagnostic: String,
): CallsCoverageSearchResult {
    return CallsCoverageSearchResult(
        status = status,
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
        diagnostic = diagnostic,
    )
}

internal data class CallsCoverageCandidateContext(
    val method: EtsMethod,
    val inputDomains: List<org.usvm.ts.pbt.model.PropertyInput>,
    val symbolicInputs: CallsSymbolicInputs,
    val entryPointId: String? = null,
    val observationFields: Map<String, org.usvm.ts.pbt.model.PropertyDomain>? = null,
)

/**
 * Shared snapshot cap and input deduplication per origin and entry point.
 * Suspended snapshots require neither results nor novelty.
 */
internal class CompletedCoverageCandidateObserver(
    contexts: List<CallsCoverageCandidateContext>,
    private val coverageStatements: Set<EtsStmt>,
    private val candidateCap: Int,
    private val budgetMillis: Long,
    private val machineCallStarted: TimeSource.Monotonic.ValueTimeMark,
    private val onCandidate: (CallsCoverageCandidate) -> Unit,
    private val onExtractionFailure: (String) -> Unit,
    private val requireEntryPointWitness: Boolean = false,
    private val emitSuspendedCandidates: Boolean = false,
) : UMachineObserver<TsState> {
    private val contextsByMethod = contexts.associateBy(CallsCoverageCandidateContext::method)
    private val coveredStatements = hashSetOf<EtsStmt>()
    private val inputKeys = hashSetOf<String>()
    private val witnessedMethods = hashSetOf<EtsMethod>()
    private var machineStarted: TimeSource.Monotonic.ValueTimeMark? = null
    private var machineStopped = false

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

    var suspendedStates: Int = 0
        private set

    private var suspendedExtractionElapsedNanos: Long = 0
    private var frontierFlushElapsedNanos: Long = 0

    val suspendedExtractionElapsedMillis: Long
        get() = suspendedExtractionElapsedNanos / NANOS_PER_MILLISECOND

    val frontierFlushElapsedMillis: Long
        get() = frontierFlushElapsedNanos / NANOS_PER_MILLISECOND

    override fun onMachineStarted() {
        machineSetupElapsedMillis = machineCallStarted.elapsedNow().inWholeMilliseconds
        machineStarted = TimeSource.Monotonic.markNow()
    }

    override fun onMachineStopped() {
        searchElapsedMillis = elapsedSinceMachineStart()
        machineStopped = true
    }

    fun finishTiming() {
        if (machineStarted == null) {
            machineSetupElapsedMillis = machineCallStarted.elapsedNow().inWholeMilliseconds
        } else if (!machineStopped) {
            searchElapsedMillis = elapsedSinceMachineStart()
            machineStopped = true
        }
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        executedSteps++
        if (elapsedSinceMachineStart() <= budgetMillis) {
            stepsWithinBudget++
        }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        if (!stateReachable) return

        val context = contextsByMethod[state.entrypoint] ?: return
        val method = context.method
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
            statement in coverageStatements
        }
        val newStatements = pathStatements.count { statement -> statement !in coveredStatements }
        val needsWitness = requireEntryPointWitness && method !in witnessedMethods
        if (newStatements == 0 && !needsWitness) return

        emitCandidate(
            state = state,
            context = context,
            pathStatements = pathStatements,
            newStatements = newStatements,
            completion = completion,
            origin = CallsCoverageCandidateOrigin.TERMINATED,
        )
    }

    fun collectStoppedState(state: TsState) {
        if (!emitSuspendedCandidates) return

        val started = TimeSource.Monotonic.markNow()
        suspendedStates++
        try {
            val context = contextsByMethod[state.entrypoint] ?: return
            emitCandidate(
                state = state,
                context = context,
                pathStatements = emptySet(),
                newStatements = 0,
                completion = null,
                origin = CallsCoverageCandidateOrigin.SUSPENDED,
            )
        } finally {
            val elapsedNanos = started.elapsedNow().inWholeNanoseconds
            suspendedExtractionElapsedNanos += elapsedNanos
            if (machineStopped) frontierFlushElapsedNanos += elapsedNanos
        }
    }

    private fun emitCandidate(
        state: TsState,
        context: CallsCoverageCandidateContext,
        pathStatements: Set<EtsStmt>,
        newStatements: Int,
        completion: CallsCoverageCompletion?,
        origin: CallsCoverageCandidateOrigin,
    ) {
        if (selectedStates >= candidateCap) {
            capReached = true
            return
        }
        selectedStates++
        val candidate = runCatching {
            val inputDomains = context.inputDomains
            val symbolicInputs = context.symbolicInputs
            val extractedInputs = symbolicInputs.resolve(state)
            val canResolveAnyModel = context.observationFields == null ||
                origin == CallsCoverageCandidateOrigin.SUSPENDED
            val eligibleInputs = if (canResolveAnyModel) {
                extractedInputs
            } else {
                // Output/state extraction uses the first model too; do not pair it with another model's inputs.
                extractedInputs.take(1)
            }
            val inputs = eligibleInputs.firstOrNull { candidate ->
                candidate.size == inputDomains.size &&
                    candidate.zip(inputDomains).all { (value, input) -> value in input.domain }
            }
            requireNotNull(inputs) { "No extracted input is inside every frozen domain" }
            val key = origin.name + ":" + context.entryPointId.orEmpty() +
                CallsExperimentJson.json.encodeToUtf8SafeString(inputs)
            if (inputKeys.add(key)) {
                val suspensionReason = state.engineFailureReason?.let { reason -> "ENGINE_FAILURE: $reason" }
                    ?: state.unsupportedReason
                CallsCoverageCandidate(
                    emittedAtMillis = elapsedSinceMachineStart(),
                    emittedAtStep = executedSteps,
                    inputs = inputs,
                    newSymbolicStatements = newStatements,
                    completion = completion,
                    entryPointId = context.entryPointId,
                    observation = context.observationFields?.takeIf {
                        origin == CallsCoverageCandidateOrigin.TERMINATED
                    }?.let { fields ->
                        extractCallsClassObservation(state = state, method = context.method, fields = fields)
                    },
                    origin = origin,
                    suspendedAtMachineStop = origin == CallsCoverageCandidateOrigin.SUSPENDED && machineStopped,
                    suspensionReason = suspensionReason.takeIf {
                        origin == CallsCoverageCandidateOrigin.SUSPENDED
                    },
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
            if (origin == CallsCoverageCandidateOrigin.TERMINATED) {
                coveredStatements += pathStatements
                witnessedMethods += context.method
            }
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

private const val NANOS_PER_MILLISECOND = 1_000_000L
