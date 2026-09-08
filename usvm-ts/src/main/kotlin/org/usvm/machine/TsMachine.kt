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
import org.usvm.machine.call.TsNoUnknownCallModels
import org.usvm.machine.call.TsProfileUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCallModelProvider
import org.usvm.machine.call.TsUnknownCallOutcome
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

/** Terminal states together with structured stop metadata for one TypeScript analysis. */
data class TsMachineAnalysisResult(
    val states: List<TsState>,
    val timedOut: Boolean,
    val unsupportedCall: Boolean,
    val engineFailed: Boolean,
)

class TsMachine(
    private val scene: EtsScene,
    override val options: UMachineOptions,
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    observer: TsInterpreterObserver? = null,
    unknownCallDispatcher: TsUnknownCallDispatcher? = null,
    unknownCallModelProvider: TsUnknownCallModelProvider = TsNoUnknownCallModels,
) : UMachine<TsState>() {
    private val graph = TsGraph(scene)
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, graph.hierarchy)
    private val components = TsComponents(typeSystem, options)
    private val ctx = TsContext(scene, components)
    private val resolvedUnknownCallDispatcher = unknownCallDispatcher ?: TsProfileUnknownCallDispatcher(
        profile = tsOptions.unknownCallProfile,
        modelProvider = unknownCallModelProvider,
        observer = observer,
    )
    private val failureTrackingUnknownCallDispatcher = FailureTrackingUnknownCallDispatcher(
        delegate = resolvedUnknownCallDispatcher,
    )
    private val interpreter = TsInterpreter(
        ctx = ctx,
        graph = graph,
        options = tsOptions,
        observer = observer,
        unknownCallDispatcher = failureTrackingUnknownCallDispatcher,
    )
    private val cfgStatistics = CfgStatisticsImpl(graph)

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): List<TsState> = analyzeWithMetadata(
        methods = methods,
        targets = targets,
        configureInitialState = configureInitialState,
    ).states

    fun analyzeWithMetadata(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
        configureInitialState: (EtsMethod, TsState) -> Unit = { _, _ -> },
    ): TsMachineAnalysisResult {
        interpreter.resetStepFailure()
        failureTrackingUnknownCallDispatcher.reset()
        val initialStates = mutableMapOf<EtsMethod, TsState>()
        methods.forEach { method ->
            initialStates[method] = interpreter.getInitialState(method, targets) {
                configureInitialState(method, this)
            }
        }

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

        return TsMachineAnalysisResult(
            states = statesCollector.collectedStates,
            timedOut = timedOut,
            unsupportedCall = failureTrackingUnknownCallDispatcher.pathStopped,
            engineFailed = interpreter.stepFailed,
        )
    }

    override fun close() {
        components.close()
    }
}

private class FailureTrackingUnknownCallDispatcher(
    private val delegate: TsUnknownCallDispatcher,
) : TsUnknownCallDispatcher {
    var pathStopped: Boolean = false
        private set

    override fun dispatch(scope: org.usvm.machine.interpreter.TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
        val outcome = delegate.dispatch(scope, call)
        if (outcome == TsUnknownCallOutcome.PATH_STOPPED) {
            pathStopped = true
        }

        return outcome
    }

    fun reset() {
        pathStopped = false
    }
}
