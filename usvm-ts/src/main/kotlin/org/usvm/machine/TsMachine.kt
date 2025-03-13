package org.usvm.machine

import mu.KotlinLogging
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.api.targets.TsTarget
import org.usvm.machine.interpreter.TsInterpreter
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.model.TsMethod
import org.usvm.model.TsScene
import org.usvm.model.TsStmt
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
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.humanReadableSignature
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class TsMachine(
    private val scene: TsScene,
    private val options: UMachineOptions,
) : UMachine<TsState>() {
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds)
    private val components = TsComponents(typeSystem, options)
    private val ctx = TsContext(scene, components)
    private val graph = TsGraph(scene)
    private val interpreter = TsInterpreter(ctx, graph)
    private val cfgStatistics = CfgStatisticsImpl(graph)

    fun analyze(
        methods: List<TsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): List<TsState> {
        val initialStates = mutableMapOf<TsMethod, TsState>()
        methods.forEach { initialStates[it] = interpreter.getInitialState(it, targets) }

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD, CoverageZone.TRANSITIVE -> methods.toHashSet()
                CoverageZone.CLASS -> TODO("Unsupported yet")
            }

        val coverageStatistics = CoverageStatistics<TsMethod, TsStmt, TsState>(methodsToTrackCoverage, graph)

        val callGraphStatistics: PlainCallGraphStatistics<TsMethod> =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> TODO("Unsupported yet")
            }

        val timeStatistics = TimeStatistics<TsMethod, TsState>()

        val pathSelector = createPathSelector(
            initialStates,
            options,
            graph,
            timeStatistics,
            { coverageStatistics },
            { cfgStatistics },
            { callGraphStatistics },
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

        val stepsStatistics = StepsStatistics<TsMethod, TsState>()

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

        if (logger.isInfoEnabled) {
            observers.add(
                StatisticsByMethodPrinter(
                    { methods },
                    logger::info,
                    { it.humanReadableSignature },
                    coverageStatistics,
                    timeStatistics,
                    stepsStatistics
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

        return statesCollector.collectedStates
    }

    override fun close() {
        components.close()
    }
}
