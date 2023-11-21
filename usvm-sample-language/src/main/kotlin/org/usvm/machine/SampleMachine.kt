package org.usvm.machine

import kotlinx.collections.immutable.persistentListOf
import org.usvm.StateCollectionStrategy
import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.language.Stmt
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.ListStatesCollector
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CallGraphStatisticsImpl
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.targets.UTargetsSet

/**
 * Entry point for a sample language analyzer.
 */
class SampleMachine(
    program: Program,
    private val options: UMachineOptions
) : UMachine<SampleState>() {
    private val applicationGraph = SampleApplicationGraph(program)
    private val typeSystem = SampleTypeSystem(options.typeOperationsTimeout)
    private val components = SampleLanguageComponents(typeSystem, options)
    private val ctx = UContext(components)
    private val solver = ctx.solver<SampleType>()

    private val interpreter = SampleInterpreter(ctx, applicationGraph)
    private val resultModelConverter = ResultModelConverter(ctx)

    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    fun analyze(
        methods: List<Method<*>>,
        resultCollector: StatesCollector<ProgramExecutionResult>,
        targets: List<SampleTarget> = emptyList()
    ) {
        logger.debug("{}.analyze({})", this, methods)
        val initialStates = mutableMapOf<Method<*>, SampleState>()
        methods.forEach {
            initialStates[it] = getInitialState(it, targets)
        }

        val coverageStatistics: CoverageStatistics<Method<*>, Stmt, SampleState> = CoverageStatistics(methods.toSet(), applicationGraph)

        val callGraphStatistics =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> CallGraphStatisticsImpl(
                    options.targetSearchDepth,
                    applicationGraph
                )
            }

        val timeStatistics = TimeStatistics<Method<*>, SampleState>()

        val pathSelector = createPathSelector(
            initialStates,
            options,
            applicationGraph,
            timeStatistics,
            { coverageStatistics },
            { cfgStatistics },
            { callGraphStatistics }
        )

        val stateCollector = object : StatesCollector<SampleState> {
            override val count: Int
                get() = resultCollector.count

            override fun addState(state: SampleState) {
                resultCollector.addState(resultModelConverter.convert(state, state.entrypoint))
            }
        }

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector(stateCollector, coverageStatistics) {
                    it.exceptionRegister != null
                }
                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector(stateCollector)
            }

        val stepsStatistics = StepsStatistics<Method<*>, SampleState>()

        val stopStrategy = createStopStrategy(
            options,
            targets,
            { timeStatistics },
            { stepsStatistics },
            { coverageStatistics },
            { stateCollector.count }
        )

        val observers = mutableListOf<UMachineObserver<SampleState>>(coverageStatistics)
        observers.add(statesCollector)
        observers.add(timeStatistics)
        observers.add(stepsStatistics)

        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }

        run(
            interpreter = interpreter,
            pathSelector = pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )
    }

    fun analyze(
        method: Method<*>,
        resultCollector: StatesCollector<ProgramExecutionResult>,
        targets: List<SampleTarget> = emptyList()
    ) = analyze(listOf(method), resultCollector, targets)

    fun analyze(method: Method<*>, targets: List<SampleTarget> = emptyList()): List<ProgramExecutionResult> {
        val collector = ListStatesCollector<ProgramExecutionResult>()
        analyze(method, collector, targets)
        return collector.collectedStates
    }

    private fun getInitialState(
        method: Method<*>,
        targets: List<SampleTarget>
    ): SampleState =
        SampleState(ctx, method, targets = UTargetsSet.from(targets)).apply {
            addEntryMethodCall(applicationGraph, method)
            val model = solver.emptyModel()
            models = persistentListOf(model)
        }

    private fun isStateTerminated(state: SampleState): Boolean {
        return state.callStack.isEmpty() || state.exceptionRegister != null
    }

    override fun close() {
        solver.close()
        ctx.close()
    }
}
