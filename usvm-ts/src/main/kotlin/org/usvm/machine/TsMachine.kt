package org.usvm.machine

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.CoverageZone
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.api.targets.TsTarget
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsModelUnknownCallDispatcher
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelCatalog
import org.usvm.machine.call.deduplicateEtsFilesBySignature
import org.usvm.machine.interpreter.TsInterpreter
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsConstructorInputGuard
import org.usvm.machine.types.TsInitialStateInputGuard
import org.usvm.machine.types.TsTypeSystem
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StatisticsByMethodPrinter
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.TsStateVisualizer
import org.usvm.util.canonicalClassDeclarations
import org.usvm.util.humanReadableSignature
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/** Whether symbolic analysis exhausted its paths or a configured strategy stopped it. */
enum class TsAnalysisStopReason {
    EXHAUSTED,
    STOPPED,
}

/** Collected states together with the machine completion kind. */
data class TsAnalysisResult(
    val states: List<TsState>,
    val stopReason: TsAnalysisStopReason,
    /** Reasons for satisfiable paths excluded by an explicit engine model bound. */
    val unsupportedPaths: List<String>,
    val infeasibleEntryPoints: List<String> = emptyList(),
)

/** Terminal states and failure metadata for one TypeScript analysis. */
data class TsMachineAnalysisResult(
    val states: List<TsState>,
    val stopReason: TsAnalysisStopReason,
    val timedOut: Boolean,
    val unsupportedCall: Boolean,
    val engineFailed: Boolean,
    val unsupportedPaths: List<String>,
    /** Entry signatures whose initial constraints were UNSAT, distinct from unsupported inputs. */
    val infeasibleEntryPoints: List<String> = emptyList(),
) {
    /** Includes execution bounds and unsupported entry inputs retained in [unsupportedPaths]. */
    val runtimeLimited: Boolean get() = unsupportedPaths.isNotEmpty()
}

class TsMachine(
    scene: EtsScene,
    override val options: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    private val observer: TsInterpreterObserver? = null,
    unknownCallDispatcher: TsUnknownCallDispatcher? = null,
    unknownCallModels: TsUnknownCallModelCatalog? = null,
    /** Creates an observer with access to the analysis queue, including unsupported terminal states. */
    private val machineObserverFactory: ((UPathSelector<TsState>) -> UMachineObserver<TsState>)? = null,
) : UMachine<TsState>() {
    init {
        val hasCompatibleCoverageScope = tsOptions.classCoverageTargets == null ||
            options.coverageZone == CoverageZone.CLASS
        require(hasCompatibleCoverageScope) { "Explicit class coverage targets require CoverageZone.CLASS" }
    }

    private val resolvedUnknownCallModels = when {
        unknownCallDispatcher != null -> null
        unknownCallModels != null -> unknownCallModels
        else -> TsBuiltInUnknownCallModels.catalog(tsOptions.unknownCallModelSelection)
    }?.materializeForMachine()

    private val analysisScene = resolvedUnknownCallModels
        ?.additionalSceneFiles
        ?.takeIf { modelFiles -> modelFiles.isNotEmpty() }
        ?.let { modelFiles ->
            val files = (scene.projectFiles + scene.sdkFiles + modelFiles).deduplicateEtsFilesBySignature()
            EtsScene(
                projectFiles = files.filter { it !in scene.sdkFiles },
                sdkFiles = scene.sdkFiles,
                projectName = scene.projectName,
            )
        }
        ?: scene
    private val runtimeScene = analysisScene.withCoreRuntimeSdk()
    private val graph = TsGraph(runtimeScene)
    private val typeSystem = TsTypeSystem(runtimeScene, typeOperationsTimeout = 1.seconds, graph.hierarchy)
    private val components = TsComponents(
        typeSystem = typeSystem,
        options = options,
        solverQueryObserver = observer?.let { it::onSolverQuery },
    )
    private val ctx = TsContext(
        scene = runtimeScene,
        components = components,
        // Model bodies are reachable only through their dispatcher and its domain guards.
        applicationAndSdkClasses = scene.projectClasses + runtimeScene.sdkClasses,
        dateNowMilliseconds = tsOptions.dateNowMilliseconds,
    )
    private val failureTrackingUnknownCallObserver = FailureTrackingUnknownCallObserver(observer)
    private val resolvedUnknownCallDispatcher = unknownCallDispatcher ?: TsModelUnknownCallDispatcher(
        models = requireNotNull(resolvedUnknownCallModels),
        fallback = tsOptions.unknownCallFallback,
        observer = failureTrackingUnknownCallObserver,
    )
    private val interpreter = TsInterpreter(
        ctx = ctx,
        graph = graph,
        options = tsOptions,
        observer = observer,
        unknownCallDispatcher = resolvedUnknownCallDispatcher,
        throwExceptionOnStepFailure = options.throwExceptionOnStepFailure,
    )
    private val cfgStatistics = CfgStatisticsImpl(graph)
    private var activeAnalysis: TsAnalysisSession? = null
    private var preparingAnalysis = false
    private var closed = false

    /** Returns supported states only. Call [analyzeWithOutcome] to inspect excluded unsupported paths. */
    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): List<TsState> = analyzeWithMetadata(
        methods = methods,
        targets = targets,
        configureInitialState = configureInitialState,
    ).states

    fun analyzeWithOutcome(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): TsAnalysisResult {
        val result = analyzeWithMetadata(
            methods = methods,
            targets = targets,
            configureInitialState = configureInitialState,
        )
        return TsAnalysisResult(
            states = result.states,
            stopReason = result.stopReason,
            unsupportedPaths = result.unsupportedPaths,
            infeasibleEntryPoints = result.infeasibleEntryPoints,
        )
    }

    private fun createTimeStatistics(): TimeStatistics<EtsMethod, TsState> = TimeStatistics(
        onEntryPointTimeQuantum = { method, requested, elapsed ->
            observer?.onEntryPointTimeQuantum(
                TsEntryPointTimeQuantumEvent(entrypoint = method, requested = requested, elapsed = elapsed),
            )
        },
    )

    fun analyzeWithMetadata(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): TsMachineAnalysisResult = openAnalysis(
        methods = methods,
        targets = targets,
        configureInitialState = configureInitialState,
    ).use { session -> session.advance() }

    /** Prepares one retained analysis; close it before starting another analysis on this machine. */
    fun openAnalysis(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): TsAnalysisSession {
        check(!closed) { "Machine is closed" }
        check(activeAnalysis == null && !preparingAnalysis) {
            "Close the current analysis session before starting another"
        }

        preparingAnalysis = true
        try {
            val session = prepareAnalysis(methods.toList(), targets.toList(), configureInitialState)
            activeAnalysis = session
            return session
        } finally {
            preparingAnalysis = false
        }
    }

    private fun prepareAnalysis(
        methods: List<EtsMethod>,
        targets: List<TsTarget>,
        configureInitialState: (EtsMethod, TsState) -> Unit,
    ): TsAnalysisSession {
        interpreter.resetStepFailure()
        failureTrackingUnknownCallObserver.reset()

        val (initialStates, unsupportedPaths, infeasibleEntryPoints) =
            createInitialStates(methods, targets, configureInitialState)

        if (initialStates.isEmpty()) {
            val result = emptyAnalysisResult(unsupportedPaths, infeasibleEntryPoints)
            return TsAnalysisSession(
                advancePhase = { result },
                analysisFinished = { true },
                release = { activeAnalysis = null },
            )
        }

        val coverageStatistics = createCoverageStatistics(entryMethods = initialStates.keys)
        val timeStatistics = createTimeStatistics()
        val pathSelector = createAnalysisPathSelector(
            initialStates = initialStates,
            timeStatistics = timeStatistics,
            coverageStatistics = coverageStatistics,
        )

        val statesCollector = createStatesCollector(coverageStatistics)

        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()
        var timedOut = false
        var finished = false
        val stopStrategy = object : StopStrategy {
            val strategy = createStopStrategy(
                options,
                targets,
                timeStatisticsFactory = { timeStatistics },
                stepsStatisticsFactory = { stepsStatistics },
                coverageStatisticsFactory = { coverageStatistics },
                getCollectedStatesCount = { statesCollector.collectedStates.size },
            )

            override fun shouldStop(): Boolean {
                if (options.timeout <= kotlin.time.Duration.ZERO) {
                    timedOut = true
                    finished = true
                    return true
                }

                val result = strategy.shouldStop()
                if (result) {
                    timedOut = timeStatistics.runningTime >= options.timeout
                    finished = true
                    logger.warn { "Stop strategy finished execution: ${strategy.stopReason()}" }
                }

                return result
            }
        }

        val supportedObserver = createAnalysisObserver(
            initialStates = initialStates,
            coverageStatistics = coverageStatistics,
            statesCollector = statesCollector,
            timeStatistics = timeStatistics,
            stepsStatistics = stepsStatistics,
        )
        val outcomeObserver = UnsupportedPathsObserver(
            delegate = supportedObserver,
            unsupportedPaths = unsupportedPaths,
        )
        val factoryObserver = machineObserverFactory?.invoke(pathSelector)
        val executionObserver = if (factoryObserver == null) {
            outcomeObserver
        } else {
            CompositeUMachineObserver(outcomeObserver, factoryObserver)
        }

        var lastResult: TsMachineAnalysisResult? = null
        return TsAnalysisSession(
            advancePhase = { maxSteps ->
                if (finished) {
                    requireNotNull(lastResult)
                } else {
                    require(maxSteps == null || initialStates.size == 1) {
                        "Bounded analysis phases require one runnable entry point"
                    }

                    val phaseStartSteps = stepsStatistics.totalSteps
                    val phaseStopStrategy = StopStrategy {
                        val analysisStopped = stopStrategy.shouldStop()
                        val phaseStopped = maxSteps != null && stepsStatistics.totalSteps - phaseStartSteps >= maxSteps
                        analysisStopped || phaseStopped
                    }

                    run(
                        interpreter = interpreter,
                        pathSelector = pathSelector,
                        observer = executionObserver,
                        isStateTerminated = { state -> state.callStack.isEmpty() },
                        stopStrategy = phaseStopStrategy,
                    )

                    val exhausted = pathSelector.isEmpty()
                    finished = finished || exhausted
                    val stopReason = if (exhausted) TsAnalysisStopReason.EXHAUSTED else TsAnalysisStopReason.STOPPED
                    val result = TsMachineAnalysisResult(
                        states = statesCollector.collectedStates.toList(),
                        stopReason = stopReason,
                        timedOut = timedOut,
                        unsupportedCall = failureTrackingUnknownCallObserver.pathStopped,
                        engineFailed = interpreter.stepFailed,
                        unsupportedPaths = unsupportedPaths.toList(),
                        infeasibleEntryPoints = infeasibleEntryPoints,
                    )
                    lastResult = result
                    result
                }
            },
            analysisFinished = { finished },
            release = { activeAnalysis = null },
        )
    }

    private fun createCoverageStatistics(
        entryMethods: Set<EtsMethod>,
    ): CoverageStatistics<EtsMethod, EtsStmt, TsState> {
        val methodsToTrackCoverage = when (options.coverageZone) {
            CoverageZone.METHOD, CoverageZone.TRANSITIVE -> entryMethods.toHashSet()
            CoverageZone.CLASS -> classCoverageMethods(entryMethods = entryMethods)
        }

        return CoverageStatistics(methods = methodsToTrackCoverage, applicationGraph = graph)
    }

    private fun createAnalysisPathSelector(
        initialStates: Map<EtsMethod, TsState>,
        timeStatistics: TimeStatistics<EtsMethod, TsState>,
        coverageStatistics: CoverageStatistics<EtsMethod, EtsStmt, TsState>,
    ): UPathSelector<TsState> {
        val callGraphStatistics: PlainCallGraphStatistics<EtsMethod> = when (options.targetSearchDepth) {
            0u -> PlainCallGraphStatistics()
            else -> TODO("Unsupported yet")
        }

        return createPathSelector(
            initialStates = initialStates,
            options = options,
            applicationGraph = graph,
            timeStatistics = timeStatistics,
            coverageStatisticsFactory = { coverageStatistics },
            cfgStatisticsFactory = { cfgStatistics },
            callGraphStatisticsFactory = { callGraphStatistics },
        )
    }

    private fun createAnalysisObserver(
        initialStates: Map<EtsMethod, TsState>,
        coverageStatistics: CoverageStatistics<EtsMethod, EtsStmt, TsState>,
        statesCollector: StatesCollector<TsState>,
        timeStatistics: TimeStatistics<EtsMethod, TsState>,
        stepsStatistics: StepsStatistics<EtsMethod, TsState>,
    ): UMachineObserver<TsState> {
        val observers = mutableListOf<UMachineObserver<TsState>>(coverageStatistics, statesCollector)
        if (tsOptions.enableVisualization) {
            observers += TsStateVisualizer()
        }

        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }

        observers.add(timeStatistics)
        observers.add(stepsStatistics)
        machineObserver?.let { observers.add(it) }

        if (logger.isInfoEnabled) {
            observers.add(
                StatisticsByMethodPrinter(
                    getMethods = { initialStates.keys.toList() },
                    print = logger::info,
                    getMethodSignature = { it.humanReadableSignature },
                    coverageStatistics = coverageStatistics,
                    timeStatistics = timeStatistics,
                    stepsStatistics = stepsStatistics,
                )
            )
        }

        return CompositeUMachineObserver(observers)
    }

    private fun createStatesCollector(
        coverageStatistics: CoverageStatistics<EtsMethod, EtsStmt, TsState>,
    ): StatesCollector<TsState> = when (options.stateCollectionStrategy) {
        StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<TsState>(coverageStatistics) {
            it.methodResult is TsMethodResult.TsException
        }
        StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
        StateCollectionStrategy.ALL -> AllStatesCollector()
    }

    private fun classCoverageMethods(entryMethods: Set<EtsMethod>): Set<EtsMethod> {
        val targets = tsOptions.classCoverageTargets
        val classes = if (targets == null) {
            entryMethods.map { method ->
                requireNotNull(method.enclosingClass) {
                    "Class coverage requires a declaring class for ${method.signature}"
                }
            }
        } else {
            val classesBySignature = runtimeScene.projectAndSdkClasses
                .groupBy { owner -> owner.signature }
                .mapValues { (_, declarations) -> canonicalClassDeclarations(declarations) }
            val unresolvedTargets = targets.filter { target -> classesBySignature[target]?.size != 1 }
            require(unresolvedTargets.isEmpty()) {
                "Class coverage targets must identify one scene class each: $unresolvedTargets"
            }

            targets.map { target -> classesBySignature.getValue(target).single() }
        }
        val coveredMethods = classes.flatMapTo(hashSetOf()) { owner ->
            owner.methods.filter { candidate ->
                candidate.signature.enclosingClass == owner.signature && candidate.cfg.stmts.isNotEmpty()
            }
        }

        return if (targets == null) coveredMethods + entryMethods else coveredMethods
    }

    private fun emptyAnalysisResult(
        unsupportedPaths: Set<String>,
        infeasibleEntryPoints: List<String>,
    ) = TsMachineAnalysisResult(
        states = emptyList(),
        stopReason = TsAnalysisStopReason.EXHAUSTED,
        timedOut = false,
        unsupportedCall = false,
        engineFailed = false,
        unsupportedPaths = unsupportedPaths.toList(),
        infeasibleEntryPoints = infeasibleEntryPoints,
    )

    private data class InitialStatesResult(
        val initialStates: Map<EtsMethod, TsState>,
        val unsupportedPaths: MutableSet<String>,
        val infeasibleEntryPoints: List<String>,
    )

    private fun createInitialStates(
        methods: List<EtsMethod>,
        targets: List<TsTarget>,
        configureInitialState: (EtsMethod, TsState) -> Unit,
    ): InitialStatesResult {
        val initialStates = mutableMapOf<EtsMethod, TsState>()
        val unsupportedPaths = mutableSetOf<String>()
        val infeasibleEntryPoints = mutableListOf<String>()
        val constructorInputs = TsConstructorInputGuard()
        val initialStateInputs = TsInitialStateInputGuard()

        methods.forEach { method ->
            val unsupportedReason = constructorInputs.unsupportedReason(method)
                ?: initialStateInputs.unsupportedReason(method)
            if (unsupportedReason != null) {
                unsupportedPaths += unsupportedReason
            } else {
                val initialState = interpreter.getInitialStateOrNull(method, targets) {
                    configureInitialState(method, this)
                }
                if (initialState != null) {
                    initialStates[method] = initialState
                } else {
                    infeasibleEntryPoints += method.humanReadableSignature
                }
            }
        }

        return InitialStatesResult(
            initialStates = initialStates,
            unsupportedPaths = unsupportedPaths,
            infeasibleEntryPoints = infeasibleEntryPoints,
        )
    }

    override fun close() {
        if (closed) return
        check(!preparingAnalysis) { "Cannot close the machine while preparing an analysis" }

        activeAnalysis?.close()
        closed = true
        components.close()
    }
}

private class FailureTrackingUnknownCallObserver(
    private val delegate: TsInterpreterObserver?,
) : TsInterpreterObserver {
    var pathStopped: Boolean = false
        private set

    override fun onPathStopped(state: TsState, reason: String) {
        delegate?.onPathStopped(state = state, reason = reason)
    }

    override fun onUnknownCall(event: TsUnknownCallEvent) {
        val decision = event.decision
        val stopsPath = decision is TsUnknownCallDecision.ResidualFallback &&
            decision.policy == TsResidualCallPolicy.STOP_PATH
        if (stopsPath) {
            pathStopped = true
        }

        delegate?.onUnknownCall(event)
    }

    fun reset() {
        pathStopped = false
    }
}

/** Keeps excluded satisfiable paths out of the ordinary witness collector. */
private class UnsupportedPathsObserver(
    private val delegate: UMachineObserver<TsState>,
    private val unsupportedPaths: MutableSet<String>,
) : UMachineObserver<TsState> by delegate {
    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        val unsupportedReason = state.unsupportedReason
        if (unsupportedReason != null) {
            if (stateReachable) {
                unsupportedPaths += unsupportedReason
            }
            return
        }

        delegate.onStateTerminated(state, stateReachable)
    }
}
