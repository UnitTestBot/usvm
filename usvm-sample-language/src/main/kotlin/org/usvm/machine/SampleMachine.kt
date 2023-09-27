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
import org.usvm.statistics.TerminatedStateRemover
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CallGraphStatisticsImpl
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy

/**
 * Entry point for a sample language analyzer.
 */
class SampleMachine(
    program: Program,
    private val options: UMachineOptions
) : UMachine<SampleState>() {
    private val applicationGraph = SampleApplicationGraph(program)
    private val typeSystem = SampleTypeSystem()
    private val components = SampleLanguageComponents(typeSystem, options.solverType)
    private val ctx = UContext<USizeSort>(components)
    private val solver = ctx.solver<SampleType, UContext<USizeSort>>()

    private val interpreter = SampleInterpreter(ctx, applicationGraph)
    private val resultModelConverter = ResultModelConverter(ctx)

    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    fun analyze(method: Method<*>, targets: List<SampleTarget> = emptyList()): Collection<ProgramExecutionResult> {
        val initialState = getInitialState(method, targets)

        val coverageStatistics: CoverageStatistics<Method<*>, Stmt, SampleState> = CoverageStatistics(setOf(method), applicationGraph)

        val callGraphStatistics =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> CallGraphStatisticsImpl(
                    options.targetSearchDepth,
                    applicationGraph
                )
            }

        val pathSelector = createPathSelector(
            initialState,
            options,
            applicationGraph,
            { coverageStatistics },
            { cfgStatistics },
            { callGraphStatistics }
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<SampleState>(coverageStatistics) {
                    it.exceptionRegister != null
                }
                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
            }

        val stopStrategy = createStopStrategy(
            options,
            targets,
            { coverageStatistics },
            { statesCollector.collectedStates.size }
        )

        val observers = mutableListOf<UMachineObserver<SampleState>>(coverageStatistics)
        observers.add(TerminatedStateRemover())
        observers.add(statesCollector)

        run(
            interpreter = interpreter,
            pathSelector = pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

        return statesCollector.collectedStates.map { resultModelConverter.convert(it, method) }
    }

    private fun getInitialState(method: Method<*>, targets: List<SampleTarget>): SampleState =
        SampleState(ctx, targets = targets).apply {
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
