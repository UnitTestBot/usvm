package org.usvm.jacodb

import mu.KLogging
import org.jacodb.go.api.GoApplicationGraphImpl
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoProject
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.jacodb.interpreter.GoInterpreter
import org.usvm.jacodb.interpreter.GoTestInterpreter
import org.usvm.jacodb.interpreter.ProgramExecutionResult
import org.usvm.jacodb.state.GoMethodResult
import org.usvm.jacodb.state.GoState
import org.usvm.jacodb.type.GoTypeSystem
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StatisticsByMethodPrinter
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.TransitiveCoverageZoneObserver
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CallGraphStatisticsImpl
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy

val logger = object : KLogging() {}.logger

class GoMachine(
    private val project: GoProject,
    private val options: UMachineOptions,
) : UMachine<GoState>() {
    private val typeSystem = GoTypeSystem(options.typeOperationsTimeout)
    private val goApplicationGraph = GoApplicationGraphImpl(project)
    private val applicationGraph = GoApplicationGraphAdapter(goApplicationGraph)
    private val components = GoComponents(typeSystem, options)
    private val ctx = GoContext(components)
    private val interpreter = GoInterpreter(ctx)
    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)
    private val testInterpreter = GoTestInterpreter(ctx)

    override fun close() {
        ctx.close()
    }

    fun analyzeAndResolve(method: GoMethod): Collection<ProgramExecutionResult> {
        return analyze(method).map { testInterpreter.resolve(it, method) }
    }

    private fun analyze(method: GoMethod): List<GoState> {
        return analyze(listOf(method))
    }

    private fun analyze(methods: List<GoMethod>, targets: List<GoTarget> = emptyList()): List<GoState> {
        logger.debug("{}.analyze()", this)

        val initialStates = hashMapOf<GoMethod, GoState>()
        methods.forEach {
            initialStates[it] = interpreter.getInitialState(it, targets)
        }
        val timeStatistics = TimeStatistics<GoMethod, GoState>()
        val coverageStatistics = CoverageStatistics<GoMethod, GoInst, GoState>(methods.toSet(), applicationGraph)
        val callGraphStatistics =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> CallGraphStatisticsImpl(
                    options.targetSearchDepth,
                    applicationGraph
                )
            }

        val pathSelector = createPathSelector(
            initialStates,
            options,
            applicationGraph,
            timeStatistics,
            { coverageStatistics },
            { cfgStatistics },
            { callGraphStatistics }
        )
        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<GoState>(coverageStatistics) {
                    it.methodResult is GoMethodResult.Panic
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }
        val stepsStatistics = StepsStatistics<GoMethod, GoState>()
        val stopStrategy = createStopStrategy(
            options,
            targets,
            timeStatisticsFactory = { timeStatistics },
            stepsStatisticsFactory = { stepsStatistics },
            coverageStatisticsFactory = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

        val observers = mutableListOf<UMachineObserver<GoState>>(coverageStatistics)
        observers.add(statesCollector)
        observers.add(timeStatistics)
        observers.add(stepsStatistics)
        if (options.coverageZone != CoverageZone.METHOD) {
            observers.add(
                TransitiveCoverageZoneObserver(
                    initialMethods = methods,
                    methodExtractor = { state -> applicationGraph.methodOf(state.currentStatement) },
                    addCoverageZone = { coverageStatistics.addCoverageZone(it) },
                    ignoreMethod = { false }
                )
            )
        }
        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }
        if (logger.isInfoEnabled) {
            observers.add(
                StatisticsByMethodPrinter(
                    { methods },
                    logger::info,
                    { it.toString() },
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
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

        return statesCollector.collectedStates
    }

    private fun isStateTerminated(state: GoState): Boolean {
        return state.callStack.isEmpty()
    }
}
