package org.usvm.interpreter

import kotlinx.collections.immutable.persistentListOf
import io.ksmt.solver.yices.KYicesSolver
import org.usvm.UAnalyzer
import org.usvm.UContext
import org.usvm.UPathConstraintsSet
import org.usvm.UPathSelector
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.model.UModelBase
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.ps.DfsPathSelector
import org.usvm.solver.USatResult
import org.usvm.solver.USolverBase

/**
 * Entry point for a sample language analyzer.
 */
class SampleAnalyzer(
    program: Program,
    val maxStates: Int = 40,
) : UAnalyzer<ExecutionState, Method<*>>() {
    private val applicationGraph = DefaultSampleApplicationGraph(program)
    private val ctx = UContext()
    private val typeSystem = SampleTypeSystem()

    private val solver = run {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field<*>, SampleType, Method<*>>(
            ctx,
            typeSystem
        )
        USolverBase(ctx, KYicesSolver(ctx), translator, decoder)
    }

    private val interpreter = SampleInterpreter(ctx, applicationGraph, solver)
    private val resultModelConverter = ResultModelConverter(ctx)

    fun analyze(method: Method<*>): Collection<ProgramExecutionResult> {
        val collectedStates = mutableListOf<ExecutionState>()
        run(
            method,
            onState = { state ->
                if (!isInterestingState(state)) {
                    collectedStates += state
                }
            },
            continueAnalyzing = ::isInterestingState,
            shouldStop = { collectedStates.size >= maxStates }
        )
        return collectedStates.map { resultModelConverter.convert(it, method) }
    }

    private fun getInitialState(solver: USolverBase<Field<*>, SampleType>, method: Method<*>): ExecutionState =
        ExecutionState(ctx, typeSystem).apply {
            addEntryMethodCall(applicationGraph, method)
            val solverResult = solver.check(UPathConstraintsSet(ctx.trueExpr))
            val satResult = solverResult as USatResult<UModelBase<Field<*>, SampleType>>
            val model = satResult.model
            models = persistentListOf(model)
        }

    override fun getInterpreter(target: Method<*>) = interpreter

    override fun getPathSelector(target: Method<*>): UPathSelector<ExecutionState> {
        val ps = DfsPathSelector<ExecutionState>()
        val initialState = getInitialState(solver, target)
        ps.add(initialState, producedStates = emptyList())
        return ps
    }

    private fun isInterestingState(state: ExecutionState): Boolean {
        return state.callStack.isNotEmpty() && state.exceptionRegister == null
    }
}