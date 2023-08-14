package org.usvm.machine

import kotlinx.collections.immutable.persistentListOf
import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.language.Stmt
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.CoveredNewStatesCollector
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.TerminatedStateRemover
import org.usvm.statistics.UMachineObserver
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
    private val ctx = UContext(components)
    private val solver = ctx.solver<Field<*>, SampleType, Method<*>, UContext>()

    private val interpreter = SampleInterpreter(ctx, applicationGraph)
    private val resultModelConverter = ResultModelConverter(ctx)

    private val distanceStatistics = DistanceStatistics(applicationGraph)

    fun analyze(
        method: Method<*>
    ): Collection<ProgramExecutionResult> {
        val initialState = getInitialState(method)

        val coverageStatistics: CoverageStatistics<Method<*>, Stmt, SampleState> = CoverageStatistics(setOf(method), applicationGraph)

        val pathSelector = createPathSelector(
            initialState,
            options,
            { coverageStatistics },
            { distanceStatistics }
        )

        val statesCollector = CoveredNewStatesCollector<SampleState>(coverageStatistics) { it.exceptionRegister != null }
        val stopStrategy = createStopStrategy(options, { coverageStatistics }, { statesCollector.collectedStates.size })

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

    private fun getInitialState(method: Method<*>): SampleState =
        SampleState(ctx).apply {
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
