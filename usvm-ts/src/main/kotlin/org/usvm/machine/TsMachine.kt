package org.usvm.machine

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.USort
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
)

/** Analysis-wide observations; collected states alone do not prove that every path completed. */
data class TsMachineAnalysisResult(
    val states: List<TsState>,
    val stopReason: TsAnalysisStopReason,
    val timedOut: Boolean,
    /** A STOP_PATH residual decision observed from the machine-owned model dispatcher. */
    val unsupportedCall: Boolean,
    val engineFailed: Boolean,
    val runtimeLimited: Boolean,
)

class TsMachine(
    scene: EtsScene,
    override val options: UMachineOptions,
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    observer: TsInterpreterObserver? = null,
    unknownCallDispatcher: TsUnknownCallDispatcher? = null,
    unknownCallModels: TsUnknownCallModelCatalog? = null,
    private val initialStateConfigurator: (TsState) -> Unit = {},
    private val initialParameterSortOverride: (TsContext, Int) -> USort? = { _, _ -> null },
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
    private val graph = TsGraph(analysisScene)
    private val typeSystem = TsTypeSystem(analysisScene, typeOperationsTimeout = 1.seconds, graph.hierarchy)
    private val components = TsComponents(typeSystem, options)
    private val ctx = TsContext(
        scene = analysisScene,
        components = components,
        applicationAndSdkClasses = scene.projectAndSdkClasses,
        dateNowMilliseconds = tsOptions.dateNowMilliseconds,
    )
    private val analysisObserver = AnalysisTrackingObserver(observer ?: object : TsInterpreterObserver {})
    private val resolvedUnknownCallDispatcher = unknownCallDispatcher ?: TsModelUnknownCallDispatcher(
        models = requireNotNull(resolvedUnknownCallModels),
        fallback = tsOptions.unknownCallFallback,
        observer = analysisObserver,
    )
    private val interpreter = TsInterpreter(
        ctx = ctx,
        graph = graph,
        options = tsOptions,
        observer = analysisObserver,
        unknownCallDispatcher = resolvedUnknownCallDispatcher,
        throwExceptionOnStepFailure = options.throwExceptionOnStepFailure,
    )
    private val cfgStatistics = CfgStatisticsImpl(graph)

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): List<TsState> = analyzeWithOutcome(methods = methods, targets = targets).states

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit,
    ): List<TsState> = analyzeWithMetadata(methods, targets, configureInitialState).states

    fun analyzeWithOutcome(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): TsAnalysisResult {
        val result = analyzeWithMetadata(methods = methods, targets = targets)
        return TsAnalysisResult(states = result.states, stopReason = result.stopReason)
    }

    /**
     * The constructor configurator runs before [configureInitialState], both before the initial solver query.
     * A caller-supplied dispatcher owns its telemetry; [TsMachineAnalysisResult.unsupportedCall] only tracks
     * residual decisions made by this machine's default dispatcher. Runtime limitations are tracked separately.
     */
    fun analyzeWithMetadata(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): TsMachineAnalysisResult {
        interpreter.resetStepFailure()
        analysisObserver.reset()

        val initialStates = createInitialStates(methods, targets, configureInitialState)

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD, CoverageZone.TRANSITIVE -> methods.toHashSet()
                CoverageZone.CLASS -> TODO("Unsupported yet")
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
                    getMethods = { methods },
                    print = logger::info,
                    getMethodSignature = { it.humanReadableSignature },
                    coverageStatistics = coverageStatistics,
                    timeStatistics = timeStatistics,
                    stepsStatistics = stepsStatistics
                )
            )
        }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
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
            unsupportedCall = analysisObserver.pathStopped,
            engineFailed = interpreter.stepFailed,
            runtimeLimited = analysisObserver.runtimeLimited,
        )
    }

    private fun createInitialStates(
        methods: List<EtsMethod>,
        targets: List<TsTarget>,
        configureInitialState: (EtsMethod, TsState) -> Unit,
    ): Map<EtsMethod, TsState> = methods.associateWith { method ->
        interpreter.getInitialState(
            method = method,
            targets = targets,
            configure = { state ->
                initialStateConfigurator(state)
                configureInitialState(method, state)
            },
            parameterSortOverride = { stackSlot -> initialParameterSortOverride(ctx, stackSlot) },
        )
    }

    override fun close() {
        components.close()
    }
}

private class AnalysisTrackingObserver(
    private val delegate: TsInterpreterObserver,
) : TsInterpreterObserver by delegate {
    var pathStopped: Boolean = false
        private set
    var runtimeLimited: Boolean = false
        private set

    override fun onUnknownCall(event: TsUnknownCallEvent) {
        val decision = event.decision
        if (decision is TsUnknownCallDecision.ResidualFallback && decision.policy == TsResidualCallPolicy.STOP_PATH) {
            pathStopped = true
        }
        delegate.onUnknownCall(event)
    }

    override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
        runtimeLimited = true
        delegate.onRuntimeFeatureLimitation(event)
    }

    fun reset() {
        pathStopped = false
        runtimeLimited = false
    }
}
