package org.usvm.machine

import kotlinx.collections.immutable.persistentListOf
import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.language.*
import org.usvm.ps.createPathSelector
import org.usvm.statistics.*
import org.usvm.stopstrategies.createStopStrategy

/**
 * Entry point for a sample language analyzer.
 */
class SampleMachine(
    program: Program,
    val maxStates: Int = 40,
) : UMachine<SampleState>() {
    private val applicationGraph = SampleApplicationGraph(program)
    private val typeSystem = SampleTypeSystem()
    private val components = SampleLanguageComponents(typeSystem)
    private val ctx = UContext(components)
    private val solver = ctx.solver<Field<*>, SampleType, Method<*>>()

    private val interpreter = SampleInterpreter(ctx, applicationGraph)
    private val resultModelConverter = ResultModelConverter(ctx)

    private val distanceStatistics = DistanceStatistics(applicationGraph)

    fun analyze(method: Method<*>, options: SampleMachineOptions = SampleMachineOptions()): Collection<ProgramExecutionResult> {
        val initialState = getInitialState(method)

        val coverageStatistics: CoverageStatistics<Method<*>, Stmt, SampleState> = CoverageStatistics(setOf(method), applicationGraph)
        val pathsTreeStatistics = PathsTreeStatistics(initialState)

        val pathSelector = createPathSelector(
            options.pathSelectionStrategy,
            { pathsTreeStatistics },
            { coverageStatistics },
            { distanceStatistics },
            options.randomSeed
        )
        pathSelector.add(listOf(initialState))

        // TODO: implement state limit stop strategy
        val stopStrategy = createStopStrategy(options.expectedCoverage, options.stepLimit) { coverageStatistics }

        val statesCollector = CoveredNewStatesCollector<SampleState>(coverageStatistics) { it.exceptionRegister != null }

        run(
            interpreter = interpreter,
            pathSelector = pathSelector,
            observer = CompositeUMachineObserver(
                coverageStatistics,
                pathsTreeStatistics,
                statesCollector
            ),
            continueAnalyzing = ::isInterestingState,
            stopStrategy = stopStrategy,
        )

        return statesCollector.collectedStates.map { resultModelConverter.convert(it, method) }
    }

    private fun getInitialState(method: Method<*>): SampleState =
        SampleState(ctx).apply {
            addEntryMethodCall(applicationGraph, method)
            val model = solver.emptyModel()
            models = persistentListOf(model)
        }

    private fun isInterestingState(state: SampleState): Boolean {
        return state.callStack.isNotEmpty() && state.exceptionRegister == null
    }

    override fun close() {
        solver.close()
        ctx.close()
    }
}
