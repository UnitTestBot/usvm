package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.api.targets.TsTarget
import org.usvm.machine.interpreter.TsInterpreter
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import kotlin.time.Duration.Companion.seconds

class TsMachine(
    private val project: EtsScene,
    private val options: UMachineOptions,
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    observer: TsInterpreterObserver? = null,
) : UMachine<TsState>() {
    private val typeSystem = TsTypeSystem(typeOperationsTimeout = 1.seconds, project)
    private val components = TsComponents(typeSystem, options)
    private val ctx = TsContext(project, components)
    private val graph = TsGraph(project)
    private val interpreter = TsInterpreter(ctx, graph, tsOptions, observer)
    private val cfgStatistics = CfgStatisticsImpl(graph)

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): List<TsState> {
        val initialStates = mutableMapOf<EtsMethod, TsState>()
        methods.forEach { initialStates[it] = interpreter.getInitialState(it, targets) }

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

        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()

        val stopStrategy = createStopStrategy(
            options,
            targets,
            timeStatisticsFactory = { timeStatistics },
            stepsStatisticsFactory = { stepsStatistics },
            coverageStatisticsFactory = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

        observers.add(timeStatistics)
        observers.add(stepsStatistics)
        machineObserver?.let { observers.add(it) }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = { state -> state.callStack.isEmpty() },
            stopStrategy = stopStrategy
        )

        return statesCollector.collectedStates
    }

    override fun close() {
        components.close()
    }
}
