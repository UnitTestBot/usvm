package org.usvm.machine

import kotlinx.collections.immutable.persistentListOf
import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.stopstrategies.CollectedStatesLimitStrategy

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

    fun analyze(method: Method<*>): Collection<ProgramExecutionResult> {
        val collectedStates = mutableListOf<SampleState>()
        val stoppingStrategy = CollectedStatesLimitStrategy(maxStates)
        run(
            interpreter = interpreter,
            pathSelector = getPathSelector(method),
            onState = { state ->
                if (!isInterestingState(state)) {
                    collectedStates += state
                    stoppingStrategy.incrementStatesCount()
                }
            },
            continueAnalyzing = ::isInterestingState,
            stoppingStrategy = stoppingStrategy,
        )
        return collectedStates.map { resultModelConverter.convert(it, method) }
    }

    fun getPathSelector(target: Method<*>): UPathSelector<SampleState> {
        val ps = DfsPathSelector<SampleState>()
        val initialState = getInitialState(target)
        ps.add(listOf(initialState))
        return ps
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