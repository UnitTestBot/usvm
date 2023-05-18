package org.usvm.interpreter

import io.ksmt.solver.yices.KYicesSolver
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.UTypeSystem
import org.usvm.constraints.UPathConstraints
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.model.UModelBase
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.ps.DfsPathSelector
import org.usvm.solver.USatResult
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem
) : UComponents<Field<*>, SampleType, Method<*>> {
    override fun mkSolver(ctx: UContext): USolverBase<Field<*>, SampleType, Method<*>> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field<*>, SampleType, Method<*>>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Field<*>, SampleType>(ctx)

        return USolverBase(ctx, KYicesSolver(ctx), translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<SampleType> = typeSystem
}

/**
 * Entry point for a sample language analyzer.
 */
class SampleMachine(
    program: Program,
    val maxStates: Int = 40,
) : UMachine<SampleState, Method<*>>() {
    private val applicationGraph = SampleApplicationGraph(program)
    private val typeSystem = SampleTypeSystem()
    private val components = SampleLanguageComponents(typeSystem)
    private val ctx = UContext(components)
    private val solver = ctx.solver<Field<*>, SampleType, Method<*>>()

    private val interpreter = SampleInterpreter(ctx, applicationGraph)
    private val resultModelConverter = ResultModelConverter(ctx)

    fun analyze(method: Method<*>): Collection<ProgramExecutionResult> {
        val collectedStates = mutableListOf<SampleState>()
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

    private fun getInitialState(method: Method<*>): SampleState =
        SampleState(ctx).apply {
            addEntryMethodCall(applicationGraph, method)
            val solverResult = solver.check(UPathConstraints(ctx), useSoftConstraints = true)
            val satResult = solverResult as USatResult<UModelBase<Field<*>, SampleType>>
            val model = satResult.model
            models = persistentListOf(model)
        }

    override fun getInterpreter(target: Method<*>) = interpreter

    override fun getPathSelector(target: Method<*>): UPathSelector<SampleState> {
        val ps = DfsPathSelector<SampleState>()
        val initialState = getInitialState(target)
        ps.add(sequenceOf(initialState))
        return ps
    }

    private fun isInterestingState(state: SampleState): Boolean {
        return state.callStack.isNotEmpty() && state.exceptionRegister == null
    }
}