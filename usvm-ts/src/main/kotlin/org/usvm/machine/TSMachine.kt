package org.usvm.machine

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.api.targets.TSTarget
import org.usvm.machine.interpreter.TSInterpreter
import org.usvm.ps.createPathSelector
import org.usvm.machine.state.TSMethodResult
import org.usvm.machine.state.TSState
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

class TSMachine(
    private val project: EtsScene,
    private val options: UMachineOptions,
) : UMachine<TSState>() {
    private val typeSystem = TSTypeSystem(typeOperationsTimeout = 1.seconds, project)
    private val components = TSComponents(typeSystem, options)
    private val ctx = TSContext(project, components)
    private val applicationGraph = TSApplicationGraph(project)
    private val interpreter = TSInterpreter(ctx, applicationGraph)
    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TSTarget> = emptyList(),
    ): List<TSState> {
        val initialStates = mutableMapOf<EtsMethod, TSState>()
        methods.forEach { initialStates[it] = interpreter.getInitialState(it, targets) }

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD, CoverageZone.TRANSITIVE -> methods.toHashSet()
                CoverageZone.CLASS -> TODO("Unsupported yet")
            }

        val coverageStatistics = CoverageStatistics<EtsMethod, EtsStmt, TSState>(
            methodsToTrackCoverage,
            applicationGraph
        )

        val callGraphStatistics: PlainCallGraphStatistics<EtsMethod> =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> TODO("Unsupported yet")
            }

        val timeStatistics = TimeStatistics<EtsMethod, TSState>()

        val pathSelector = createPathSelector(
            initialStates,
            options,
            applicationGraph,
            timeStatistics,
            { coverageStatistics },
            { cfgStatistics },
            { callGraphStatistics },
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<TSState>(coverageStatistics) {
                    it.methodResult is TSMethodResult.TSException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val observers = mutableListOf<UMachineObserver<TSState>>(coverageStatistics)
        observers.add(statesCollector)

        val stepsStatistics = StepsStatistics<EtsMethod, TSState>()

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
