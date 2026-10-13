package org.usvm.machine

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
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
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.TsStateVisualizer
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
)

/** Terminal states and failure metadata for one TypeScript analysis. */
data class TsMachineAnalysisResult(
    val states: List<TsState>,
    val stopReason: TsAnalysisStopReason,
    val timedOut: Boolean,
    val unsupportedCall: Boolean,
    val engineFailed: Boolean,
    val unsupportedPaths: List<String>,
) {
    /** Includes execution bounds and unsupported entry inputs retained in [unsupportedPaths]. */
    val runtimeLimited: Boolean get() = unsupportedPaths.isNotEmpty()
}

class TsMachine(
    scene: EtsScene,
    override val options: UMachineOptions,
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    observer: TsInterpreterObserver? = null,
    unknownCallDispatcher: TsUnknownCallDispatcher? = null,
    unknownCallModels: TsUnknownCallModelCatalog? = null,
) : UMachine<TsState>() {
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
    private val components = TsComponents(typeSystem, options)
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
        )
    }

    fun analyzeWithMetadata(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): TsMachineAnalysisResult {
        interpreter.resetStepFailure()
        failureTrackingUnknownCallObserver.reset()

        val (initialStates, unsupportedPaths) = createInitialStates(methods, targets, configureInitialState)

        if (initialStates.isEmpty()) {
            return emptyAnalysisResult(unsupportedPaths)
        }

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD, CoverageZone.TRANSITIVE -> initialStates.keys.toHashSet()
                CoverageZone.CLASS -> initialStates.keys.flatMapTo(hashSetOf()) { method ->
                    val owner = requireNotNull(method.enclosingClass) {
                        "Class coverage requires a declaring class for ${method.signature}"
                    }

                    owner.methods.filter { candidate ->
                        candidate.signature.enclosingClass == owner.signature && candidate.cfg.stmts.isNotEmpty()
                    }
                } + initialStates.keys
            }

        val coverageStatistics = CoverageStatistics<EtsMethod, EtsStmt, TsState>(
            methods = methodsToTrackCoverage,
            applicationGraph = graph,
        )

        val callGraphStatistics: PlainCallGraphStatistics<EtsMethod> =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> TODO("Unsupported yet")
            }

        val timeStatistics = TimeStatistics<EtsMethod, TsState>()

        val pathSelector = createPathSelector(
            initialStates = initialStates,
            options = options,
            applicationGraph = graph,
            timeStatistics = timeStatistics,
            coverageStatisticsFactory = { coverageStatistics },
            cfgStatisticsFactory = { cfgStatistics },
            callGraphStatisticsFactory = { callGraphStatistics },
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<TsState>(coverageStatistics) {
                    it.methodResult is TsMethodResult.TsException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val observers = mutableListOf<UMachineObserver<TsState>>(coverageStatistics)
        observers.add(statesCollector)
        if (tsOptions.enableVisualization) {
            observers += TsStateVisualizer()
        }

        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }

        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()
        var timedOut = false
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
                    return true
                }

                val result = strategy.shouldStop()
                if (result && timeStatistics.runningTime >= options.timeout) {
                    timedOut = true
                }

                if (result) {
                    logger.warn { "Stop strategy finished execution: ${strategy.stopReason()}" }
                }

                return result
            }
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
                    stepsStatistics = stepsStatistics
                )
            )
        }

        val supportedObserver = CompositeUMachineObserver(observers)
        val outcomeObserver = UnsupportedPathsObserver(supportedObserver, unsupportedPaths)

        run(
            interpreter,
            pathSelector,
            observer = outcomeObserver,
            isStateTerminated = { state -> state.callStack.isEmpty() },
            stopStrategy = stopStrategy
        )

        val stopReason = if (pathSelector.isEmpty()) {
            TsAnalysisStopReason.EXHAUSTED
        } else {
            TsAnalysisStopReason.STOPPED
        }

        return TsMachineAnalysisResult(
            states = statesCollector.collectedStates,
            stopReason = stopReason,
            timedOut = timedOut,
            unsupportedCall = failureTrackingUnknownCallObserver.pathStopped,
            engineFailed = interpreter.stepFailed,
            unsupportedPaths = unsupportedPaths.toList(),
        )
    }

    private fun emptyAnalysisResult(unsupportedPaths: Set<String>) = TsMachineAnalysisResult(
        states = emptyList(),
        stopReason = TsAnalysisStopReason.EXHAUSTED,
        timedOut = false,
        unsupportedCall = false,
        engineFailed = false,
        unsupportedPaths = unsupportedPaths.toList(),
    )

    private fun createInitialStates(
        methods: List<EtsMethod>,
        targets: List<TsTarget>,
        configureInitialState: (EtsMethod, TsState) -> Unit,
    ): Pair<Map<EtsMethod, TsState>, MutableSet<String>> {
        val initialStates = mutableMapOf<EtsMethod, TsState>()
        val unsupportedPaths = mutableSetOf<String>()
        val constructorInputs = TsConstructorInputGuard()

        methods.forEach { method ->
            val unsupportedReason = constructorInputs.unsupportedReason(method)
            if (unsupportedReason != null) {
                unsupportedPaths += unsupportedReason
            } else {
                initialStates[method] = interpreter.getInitialState(method, targets) {
                    configureInitialState(method, this)
                }
            }
        }

        return initialStates to unsupportedPaths
    }

    override fun close() {
        components.close()
    }
}

private class FailureTrackingUnknownCallObserver(
    private val delegate: TsInterpreterObserver?,
) : TsInterpreterObserver {
    var pathStopped: Boolean = false
        private set

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
