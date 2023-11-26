package org.usvm

import mu.KLogging
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.usvm.interpreter.GoInterpreter
import org.usvm.interpreter.GoTestInterpreter
import org.usvm.interpreter.ProgramExecutionResult
import org.usvm.ps.createPathSelector
import org.usvm.state.GoMethodResult
import org.usvm.state.GoState
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
import org.usvm.type.GoTypeSystem
import org.usvm.util.hasUnsupportedInstructions
import org.usvm.util.isInit
import kotlin.math.roundToInt

internal typealias USizeSort = UBv32Sort

val logger = object : KLogging() {}.logger

class GoMachine(
    private val program: GoProgram,
    private val options: UMachineOptions,
    private val customOptions: GoMachineOptions
) : UMachine<GoState>() {
    private val typeSystem = GoTypeSystem(options.typeOperationsTimeout, program.types.values)
    private val applicationGraph = GoApplicationGraph()
    private val components = GoComponents(typeSystem, options)
    private val ctx = GoContext(components)
    private val interpreter = GoInterpreter(ctx, program, applicationGraph)
    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)
    private val testInterpreter = GoTestInterpreter(ctx)

    override fun close() {
        ctx.close()
    }

    fun analyzeAndResolve(pkg: GoPackage, methodName: String): Collection<ProgramExecutionResult> {
        return analyzeAndResolve(pkg.findMethod(methodName))
    }

    fun analyzeAndResolve(method: GoMethod): Collection<ProgramExecutionResult> {
        return analyze(method).map { testInterpreter.resolve(it, method) }
    }

    private fun analyze(method: GoMethod): List<GoState> {
        return analyze(listOf(method))
    }

    private fun analyze(methodsList: List<GoMethod>, targets: List<GoTarget> = emptyList()): List<GoState> {
        logger.debug("{}.analyze()", this)
        val excludedMethods = methodsList.filter { it.hasUnsupportedInstructions() }.map { it.metName }
        if (excludedMethods.isNotEmpty()) {
            logger.warn("The following methods will be skipped due to having unsupported instructions: {}", excludedMethods)
        }

        val methods = methodsList.filter { !it.hasUnsupportedInstructions() }
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
                    methodExtractor = { state -> state.currentStatement.location.method },
                    addCoverageZone = { coverageStatistics.addCoverageZone(it) },
                    ignoreMethod = { it.isInit() || it.hasUnsupportedInstructions() }
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

        println("Total coverage: ${coverageStatistics.getTotalCoverage().roundToInt()}%")
        for (method in coverageStatistics.coverageZone) {
            println("Method ${method.metName} coverage: ${coverageStatistics.getMethodCoverage(method).roundToInt()}%")
        }
        if (coverageStatistics.getTotalCoverage().roundToInt() < 100 && !customOptions.uncoveredMethods.containsAll(methods.map { it.metName })) {
            if (customOptions.failOnNotFullCoverage) {
                throw IllegalStateException("coverage not 100%")
            } else {
                logger.warn("analysis of methods ${methods.map { it.metName }} lead to coverage < 100%")
            }
        }

        return statesCollector.collectedStates
    }

    private fun isStateTerminated(state: GoState): Boolean {
        return state.callStack.isEmpty()
    }
}
