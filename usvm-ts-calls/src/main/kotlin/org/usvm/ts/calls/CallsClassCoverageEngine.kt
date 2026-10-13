package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.usvm.CoverageZone
import org.usvm.PathSelectorFairnessStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsEntryPointTimeQuantumEvent
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsSolverQueryEvent
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.interpreter.TsInitialStateSolverUnknownException
import org.usvm.machine.state.TsState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.usvm.PlainObjectInputShapes
import kotlin.time.Duration
import kotlin.time.TimeSource

internal data class CallsClassSearchRequest(
    val harness: CallsClassPreparedHarness,
    val profile: CallsExperimentProfile,
    val frozenModelIds: Set<String>,
    val expectedNativeFrontendRevision: String,
    val seed: Long,
    val budget: Duration,
    val timeQuantum: Duration,
    val solverQueryLimit: Duration,
    val solverType: SolverType,
    val candidateCap: Int,
    val requireEntryPointWitness: Boolean = false,
    val onCandidate: (CallsCoverageCandidate) -> Unit = {},
) {
    init {
        require(budget > Duration.ZERO && timeQuantum > Duration.ZERO && timeQuantum <= budget)
        require(solverQueryLimit > Duration.ZERO && solverQueryLimit <= budget)
        require(candidateCap > 0)
    }
}

@Serializable
internal data class CallsClassScenarioMetrics(
    val scenarioId: String,
    val executedSteps: Long,
    val stepWallTimeMillis: Long,
    val completedCandidates: Int,
)

@Serializable
internal data class CallsClassMethodMetrics(
    val methodSignature: String,
    val executedSteps: Long,
    val stepWallTimeNanos: Long,
)

/** Consecutive visits to an entry point; a single remaining entry can span multiple selector quanta. */
@Serializable
internal data class CallsClassSchedulingVisit(
    val scenarioId: String,
    val startedAtMillis: Long,
    val elapsedMillis: Long,
    val requestedQuantumMillis: Long,
)

@Serializable
internal data class CallsClassTimeQuantum(
    val scenarioId: String,
    val startedAtMillis: Long,
    val requestedQuantumMillis: Long,
    val elapsedNanos: Long,
)

@Serializable
internal data class CallsClassSolverMetrics(
    val phase: String,
    val scenarioId: String?,
    val methodSignature: String?,
    val calls: Long,
    val elapsedNanos: Long,
    val failures: Long,
    val statusCounts: Map<String, Long>,
)

@Serializable
internal data class CallsClassSearchResult(
    val search: CallsCoverageSearchResult,
    val scenarios: List<CallsClassScenarioMetrics>,
    val visits: List<CallsClassSchedulingVisit>,
    val unknownCallEvents: Int,
    val methods: List<CallsClassMethodMetrics> = emptyList(),
    val timeQuanta: List<CallsClassTimeQuantum> = emptyList(),
    val solverQueries: List<CallsClassSolverMetrics> = emptyList(),
    val solverCalls: Long? = null,
    val solverElapsedMillis: Long? = null,
    val unavailableMetrics: List<String> = emptyList(),
)

/** A class owns one machine and one timeout; scenarios retain their path selectors between bounded visits. */
internal class CurrentTsCallsClassCoverageEngine {
    @Suppress("LongMethod")
    fun search(request: CallsClassSearchRequest): CallsClassSearchResult {
        val preparationStarted = TimeSource.Monotonic.markNow()
        val sourceEngine = CurrentTsCallsSymbolicEngine()
        val harness = request.harness
        val preparations = harness.functions.map { function ->
            sourceEngine.prepareFunction(
                CallsFunctionPreflightRequest(
                    sourceRoot = harness.sourceRoot,
                    project = harness.project,
                    function = function,
                    expectedNativeFrontendRevision = request.expectedNativeFrontendRevision,
                    verifySourceCheckout = false,
                ),
            )
        }
        val rejection = preparations.filterIsInstance<CallsFunctionPreparation.Rejected>().firstOrNull()
        if (rejection != null) {
            return rejectedSearch(
                diagnostic = "${rejection.reasonCode}: ${rejection.diagnostic}",
                preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds,
                status = if (rejection.status == CallsSymbolicStatus.TOOL_ERROR) {
                    CallsCoverageSearchStatus.TOOL_ERROR
                } else {
                    CallsCoverageSearchStatus.UNSUPPORTED
                },
            )
        }
        val eligible = preparations.filterIsInstance<CallsFunctionPreparation.Eligible>()
        val first = eligible.first()
        require(eligible.all { it.scene === first.scene }) { "Class scenarios must share one source scene" }
        val scope = resolveCallsSymbolicCoverageScope(
            preparation = first,
            target = CallsSymbolicCoverageClass(harness.surface.sourceFile, harness.surface.exportName),
        )
        if (scope is CallsSymbolicCoverageScope.Rejected) {
            return rejectedSearch(
                diagnostic = scope.diagnostic,
                preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds,
                status = scope.status,
            )
        }
        scope as CallsSymbolicCoverageScope.Selected
        val objectShapes = PlainObjectInputShapes(
            sourceScene = first.scene,
            domains = harness.functions.flatMap { function -> function.inputs.map(PropertyInput::domain) },
        )
        val contexts = eligible.mapIndexed { index, preparation ->
            val function = harness.functions[index]
            CallsCoverageCandidateContext(
                method = preparation.method,
                inputDomains = function.inputs,
                symbolicInputs = CallsSymbolicInputs(
                    inputs = function.inputs,
                    bindings = preparation.inputBindings,
                    lexicalEnvironment = preparation.lexicalEnvironment,
                    captures = preparation.captures,
                    sourceScene = first.scene,
                    objectShapes = objectShapes,
                ),
                entryPointId = harness.scenarios[index].id,
                observationFields = harness.observations[index],
            )
        }
        val preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds
        val byMethod = contexts.associateBy(CallsCoverageCandidateContext::method)
        val candidates = mutableListOf<CallsCoverageCandidate>()
        val extractionFailures = mutableListOf<String>()
        val machineCallStarted = TimeSource.Monotonic.markNow()
        val candidateObserver = CompletedCoverageCandidateObserver(
            contexts = contexts,
            coverageStatements = scope.statements,
            candidateCap = request.candidateCap,
            budgetMillis = request.budget.inWholeMilliseconds,
            machineCallStarted = machineCallStarted,
            onCandidate = { candidate ->
                request.onCandidate(candidate)
                candidates += candidate
            },
            onExtractionFailure = extractionFailures::add,
            requireEntryPointWitness = request.requireEntryPointWitness,
        )
        val schedulingObserver = ClassSchedulingObserver(
            scenarioIds = contexts.associate { it.method to requireNotNull(it.entryPointId) },
            requestedQuantumMillis = request.timeQuantum.inWholeMilliseconds,
            targetClass = requireNotNull(scope.classSignature),
        )
        val limitations = linkedSetOf<String>()
        var unknownCallEvents = 0
        val interpreterObserver = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += "${event.reason.name}: ${event.detail}"
            }

            override fun onUnknownCall(event: TsUnknownCallEvent) {
                unknownCallEvents++
            }

            override fun onSolverQuery(event: TsSolverQueryEvent) {
                schedulingObserver.recordSolverQuery(event)
            }

            override fun onEntryPointTimeQuantum(event: TsEntryPointTimeQuantumEvent) {
                schedulingObserver.recordTimeQuantum(event)
            }
        }
        val modelIds = if (request.profile.usesFrozenModels) request.frozenModelIds else emptySet()
        val inputCapacity = contexts.flatMap { it.inputDomains }.maxOfOrNull { it.domain.callsInputCapacity() } ?: 0
        val machineResult = runCatching {
            TsMachine(
                scene = objectShapes.scene,
                options = UMachineOptions(
                    pathSelectionStrategies = listOf(CALLS_PATH_SELECTION_STRATEGY),
                    pathSelectorFairnessStrategy = PathSelectorFairnessStrategy.CONSTANT_TIME,
                    stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
                    randomSeed = request.seed,
                    timeout = request.budget,
                    solverTimeout = request.solverQueryLimit,
                    solverType = request.solverType,
                    maxEntryPointTimeQuantum = request.timeQuantum,
                    coverageZone = CoverageZone.CLASS,
                    removeCoveredEntryPoints = false,
                    stopOnCoverage = CALLS_STOP_ON_COVERAGE,
                    stopOnTargetsReached = false,
                    throwExceptionOnStepFailure = true,
                ),
                tsOptions = TsOptions(
                    maxArraySize = maxOf(TsOptions().maxArraySize, inputCapacity),
                    unknownCallModelSelection = TsUnknownCallModelSelection.Only(modelIds),
                    unknownCallFallback = request.profile.fallback,
                    classCoverageTargets = setOf(requireNotNull(scope.classSignature)),
                    eraseTypeAssertions = true,
                ),
                machineObserver = CompositeUMachineObserver(candidateObserver, schedulingObserver),
                observer = interpreterObserver,
            ).use { machine ->
                machine.analyzeWithMetadata(
                    methods = contexts.map(CallsCoverageCandidateContext::method),
                    configureInitialState = { method, state ->
                        byMethod.getValue(method).symbolicInputs.initialize(state)
                    },
                )
            }
        }
        candidateObserver.finishTiming()
        schedulingObserver.finishTiming()
        val elapsedMillis = machineCallStarted.elapsedNow().inWholeMilliseconds
        val outcome = machineResult.getOrNull()
        val status = when {
            machineResult.exceptionOrNull() is TsInitialStateSolverUnknownException -> CallsCoverageSearchStatus.UNKNOWN
            machineResult.isFailure -> CallsCoverageSearchStatus.TOOL_ERROR
            outcome?.timedOut == true -> CallsCoverageSearchStatus.TIMEOUT
            outcome?.stopReason == TsAnalysisStopReason.EXHAUSTED -> CallsCoverageSearchStatus.EXHAUSTED
            else -> CallsCoverageSearchStatus.STOPPED
        }
        val search = CallsCoverageSearchResult(
            status = status,
            candidates = candidates,
            selectedStates = candidateObserver.selectedStates,
            executedSteps = candidateObserver.executedSteps,
            stepsWithinBudget = candidateObserver.stepsWithinBudget,
            extractionFailures = extractionFailures,
            candidateCapReached = candidateObserver.capReached,
            preparationElapsedMillis = preparationElapsedMillis,
            machineSetupElapsedMillis = candidateObserver.machineSetupElapsedMillis,
            searchElapsedMillis = candidateObserver.searchElapsedMillis,
            machineTeardownElapsedMillis = (
                elapsedMillis - candidateObserver.machineSetupElapsedMillis - candidateObserver.searchElapsedMillis
                ).coerceAtLeast(0),
            unsupportedCall = outcome?.unsupportedCall == true,
            engineFailed = outcome?.engineFailed == true,
            runtimeLimited = outcome?.runtimeLimited == true || limitations.isNotEmpty(),
            runtimeLimitationReasons = (limitations + outcome?.unsupportedPaths.orEmpty()).toList(),
            diagnostic = machineResult.exceptionOrNull()?.let { it.message ?: it::class.java.name },
        )

        return CallsClassSearchResult(
            search = search,
            scenarios = schedulingObserver.metrics(candidates),
            visits = schedulingObserver.visits,
            unknownCallEvents = unknownCallEvents,
            methods = schedulingObserver.methodMetrics(),
            timeQuanta = schedulingObserver.timeQuanta,
            solverQueries = schedulingObserver.solverMetrics(),
            solverCalls = schedulingObserver.solverMetrics().sumOf { it.calls },
            solverElapsedMillis = schedulingObserver.solverMetrics().sumOf { it.elapsedNanos } / 1_000_000,
        )
    }
}

private class ClassSchedulingObserver(
    private val scenarioIds: Map<EtsMethod, String>,
    private val requestedQuantumMillis: Long,
    private val targetClass: EtsClassSignature,
) : UMachineObserver<TsState> {
    private var machineStarted = TimeSource.Monotonic.markNow()
    private var stepStarted = TimeSource.Monotonic.markNow()
    private var currentMethod: EtsMethod? = null
    private var visitStartedAtMillis = 0L
    private val steps = mutableMapOf<EtsMethod, Long>()
    private val timesNanos = mutableMapOf<EtsMethod, Long>()
    private var executingMethod: EtsMethod? = null
    private val methodSteps = mutableMapOf<EtsMethod, Long>()
    private val methodTimesNanos = mutableMapOf<EtsMethod, Long>()
    val visits = mutableListOf<CallsClassSchedulingVisit>()
    val timeQuanta = mutableListOf<CallsClassTimeQuantum>()
    private var solverPhase = "SETUP"
    private val solverEvents = linkedMapOf<Triple<String, String?, String?>, MutableList<TsSolverQueryEvent>>()

    fun recordTimeQuantum(event: TsEntryPointTimeQuantumEvent) {
        timeQuanta += CallsClassTimeQuantum(
            scenarioId = scenarioIds.getValue(event.entrypoint),
            startedAtMillis = (machineStarted.elapsedNow() - event.elapsed).inWholeMilliseconds.coerceAtLeast(0),
            requestedQuantumMillis = event.requested.inWholeMilliseconds,
            elapsedNanos = event.elapsed.inWholeNanoseconds,
        )
    }

    fun recordSolverQuery(event: TsSolverQueryEvent) {
        val scenarioId = currentMethod?.let(scenarioIds::getValue)
        val methodSignature = executingMethod?.signature?.toString()
        solverEvents.getOrPut(Triple(solverPhase, scenarioId, methodSignature)) { mutableListOf() } += event
    }

    fun solverMetrics(): List<CallsClassSolverMetrics> = solverEvents.map { (key, events) ->
        CallsClassSolverMetrics(
            phase = key.first,
            scenarioId = key.second,
            methodSignature = key.third,
            calls = events.size.toLong(),
            elapsedNanos = events.sumOf { it.elapsedNanos },
            failures = events.count { it.failure != null }.toLong(),
            statusCounts = events.groupingBy { it.status?.name ?: "ERROR" }.eachCount().mapValues { it.value.toLong() },
        )
    }

    override fun onMachineStarted() {
        machineStarted = TimeSource.Monotonic.markNow()
        solverPhase = "SEARCH"
    }

    override fun onStatePeeked(state: TsState) {
        val elapsed = machineStarted.elapsedNow().inWholeMilliseconds
        if (currentMethod != state.entrypoint) {
            finishVisit(elapsed)
            currentMethod = state.entrypoint
            visitStartedAtMillis = elapsed
        }
        val activeMethod = if (state.callStack.isNotEmpty()) state.callStack.lastMethod() else null
        executingMethod = activeMethod?.takeIf { it.signature.enclosingClass == targetClass }
        stepStarted = TimeSource.Monotonic.markNow()
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        val elapsedNanos = stepStarted.elapsedNow().inWholeNanoseconds
        steps.merge(parent.entrypoint, 1L, Long::plus)
        timesNanos.merge(parent.entrypoint, elapsedNanos, Long::plus)
        executingMethod?.let { method ->
            methodSteps.merge(method, 1L, Long::plus)
            methodTimesNanos.merge(method, elapsedNanos, Long::plus)
        }
    }

    override fun onMachineStopped() = finishTiming()

    fun finishTiming() {
        finishVisit(machineStarted.elapsedNow().inWholeMilliseconds)
        currentMethod = null
        executingMethod = null
        solverPhase = "TEARDOWN"
    }

    fun methodMetrics(): List<CallsClassMethodMetrics> = methodSteps.map { (method, executedSteps) ->
        CallsClassMethodMetrics(
            methodSignature = method.signature.toString(),
            executedSteps = executedSteps,
            stepWallTimeNanos = methodTimesNanos[method] ?: 0,
        )
    }

    fun metrics(candidates: List<CallsCoverageCandidate>): List<CallsClassScenarioMetrics> =
        scenarioIds.map { (method, id) ->
            CallsClassScenarioMetrics(
                scenarioId = id,
                executedSteps = steps[method] ?: 0,
                stepWallTimeMillis = (timesNanos[method] ?: 0) / 1_000_000,
                completedCandidates = candidates.count { it.entryPointId == id },
            )
        }

    private fun finishVisit(elapsed: Long) {
        val method = currentMethod ?: return
        visits += CallsClassSchedulingVisit(
            scenarioId = scenarioIds.getValue(method),
            startedAtMillis = visitStartedAtMillis,
            elapsedMillis = elapsed - visitStartedAtMillis,
            requestedQuantumMillis = requestedQuantumMillis,
        )
    }
}

private fun rejectedSearch(
    diagnostic: String,
    preparationElapsedMillis: Long,
    status: CallsCoverageSearchStatus,
): CallsClassSearchResult = CallsClassSearchResult(
    search = CallsCoverageSearchResult(
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
    ),
    scenarios = emptyList(),
    visits = emptyList(),
    unknownCallEvents = 0,
    unavailableMetrics = listOf("solverCalls", "solverElapsedMillis", "exactSelectorQuantumBoundaries"),
)
