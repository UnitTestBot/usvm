package org.usvm.machine

import mu.KotlinLogging
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UCallStack
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.api.TsTarget
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
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.statistics.distances.ReachabilityKind
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.TsStateVisualizer
import org.usvm.util.humanReadableSignature
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class TsMachine(
    private val scene: EtsScene,
    override val options: UMachineOptions,
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    observer: TsInterpreterObserver? = null,
) : UMachine<TsState>() {
    private val graph = TsGraph(scene)
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, graph.hierarchy)
    private val components = TsComponents(typeSystem, options)
    private val ctx = TsContext(scene, components)
    private val interpreter = TsInterpreter(ctx, graph, tsOptions, observer)
    private val cfgStatistics = CfgStatisticsImpl(graph)

    var isFinished: Boolean = false

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): List<TsState> {
        val methods = methods
            .filterNot {
                it.parameters.isNotEmpty() && it.parameters.first().type is EtsLexicalEnvType
            }
            .filterNot {
                it.cfg.stmts.isEmpty()
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

        val distanceCalculator = MultiTargetDistanceCalculator<EtsMethod, EtsStmt, _> { stmt ->
            InterprocDistanceCalculator(
                targetLocation = stmt,
                applicationGraph = graph,
                cfgStatistics = cfgStatistics,
                callGraphStatistics = callGraphStatistics
            )
        }

        val initialStates = mutableMapOf<EtsMethod, TsState>()

        val methodsForAnalysis = if (targets.isEmpty()) {
            methods
        } else {
            methods
                .mapNotNull { method ->
                    runCatching {
                        val stack = UCallStack<EtsMethod, EtsStmt>(method)
                        val stmt = method.cfg.stmts.first()
                        val required = targets.any { target ->
                            val distance = distanceCalculator.calculateDistance(
                                stmt,
                                stack,
                                target.location!!
                            )
                            distance.reachabilityKind != ReachabilityKind.NONE
                        }

                        if (required) method else null
                    }.getOrNull()
                }
        }

        methodsForAnalysis.forEach { initialStates[it] = interpreter.getInitialState(it, targets) }

        val pathSelector = createPathSelector(
            initialStates = initialStates,
            options = options,
            applicationGraph = graph,
            timeStatistics = timeStatistics,
            coverageStatisticsFactory = { coverageStatistics },
            cfgStatisticsFactory = { cfgStatistics },
            callGraphStatisticsFactory = { callGraphStatistics },
            distanceCalculator = distanceCalculator,
        )

        val statesCollector: StatesCollector<TsState> =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector(coverageStatistics) {
                    it.methodResult is TsMethodResult.TsException || it.methodResult is TsMethodResult.MachineError
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()

                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val observers = mutableListOf<UMachineObserver<TsState>>(coverageStatistics)

        if (tsOptions.enableVisualization) {
            observers += TsStateVisualizer()
        }

        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }

        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()

        val (stepsFactory, coverageFactory, collectedStatesCount) = if (options.stopOnCoverage !in 1..100) {
            Triple({ null }, { null }, null)
        } else {
            Triple({ stepsStatistics }, { coverageStatistics }, { statesCollector.collectedStates.size })
        }

        val stopStrategy = object : StopStrategy {
            val strategy = createStopStrategy(
                options,
                targets,
                timeStatisticsFactory = { timeStatistics },
                stepsStatisticsFactory = stepsFactory,
                coverageStatisticsFactory = coverageFactory,
                getCollectedStatesCount = collectedStatesCount,
            )

            override fun shouldStop(): Boolean {
                val result = strategy.shouldStop()

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
            if (methods.size < 100) {
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
        }

        // TODO hack, for now states collector must be the last one since other collectors might depend on it
        observers.add(statesCollector)

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = { state -> state.callStack.isEmpty() },
            stopStrategy = stopStrategy
        )

        isFinished = pathSelector.isEmpty()

        return statesCollector.collectedStates
    }

    override fun close() {
        components.close()
    }
}
