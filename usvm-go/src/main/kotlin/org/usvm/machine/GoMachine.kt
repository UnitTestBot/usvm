package org.usvm.machine

import mu.KLogging
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.bridge.GoBridge
import org.usvm.machine.interpreter.GoInterpreter
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.state.GoState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.*
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CallGraphStatisticsImpl
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy

val logger = object : KLogging() {}.logger

class GoMachine(
    private val options: UMachineOptions,
) : UMachine<GoState>() {
    private val bridge = GoBridge()
    private val typeSystem = GoTypeSystem(bridge, options.typeOperationsTimeout)
    private val applicationGraph = GoApplicationGraph(bridge)
    private val components = GoComponents(typeSystem, options)
    private val ctx = GoContext(components)
    private val interpreter = GoInterpreter(bridge, ctx)
    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    override fun close() {
        ctx.close()
    }

    fun analyze(file: String, entrypoint: String, debug: Boolean): List<GoState> {
        bridge.initialize(file, debug)

        val entryPoint = bridge.getMethod(entrypoint)
        val startCode = bridge.start()
        logger.debug("Bridge start: code {}", startCode)
        if (startCode != 0) {
            return emptyList()
        }

        val results = analyze(listOf(entryPoint))
        val shutdownCode = bridge.shutdown()
        logger.debug("Bridge shutdown: code {}", shutdownCode)
        return results
    }

    private fun analyze(methods: List<GoMethod>, targets: List<GoTarget> = emptyList()): List<GoState> {
        logger.debug("{}.analyze()", this)

        val initialStates = mutableMapOf<GoMethod, GoState>()
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
