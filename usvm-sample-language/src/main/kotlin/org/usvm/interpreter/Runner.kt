package org.usvm.interpreter

import io.ksmt.solver.yices.KYicesSolver
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTypeSystem
import org.usvm.model.UModelBase
import org.usvm.constraints.UPathConstraints
import org.usvm.solver.USolverBase
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USatResult
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem
): UComponents<Field<*>, SampleType, Method<*>> {
    override fun mkSolver(ctx: UContext): USolverBase<Field<*>, SampleType, Method<*>> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field<*>, SampleType, Method<*>>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Field<*>, SampleType>(ctx)
        return USolverBase(ctx, KYicesSolver(ctx), translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<SampleType> =
        typeSystem
}

/**
 * Entry point for a sample language analyzer.
 */
class Runner(
    val program: Program,
    val maxStates: Int = 40,
) {
    private val applicationGraph = DefaultSampleApplicationGraph(program)
    private val typeSystem = SampleTypeSystem()

    fun run(method: Method<*>): List<ProgramExecutionResult> {
        val components = SampleLanguageComponents(typeSystem)
        val ctx = UContext(components)

        val resultModelConverter = ResultModelConverter(ctx, method)
        val initialState = getInitialState(ctx, ctx.solver(), method)

        val interpreter = SampleInterpreter(ctx, applicationGraph)

        val queue = ArrayDeque<ExecutionState>()
        queue.add(initialState)

        val finalStates = mutableListOf<ExecutionState>()

        while (queue.isNotEmpty() && finalStates.size < maxStates) {
            val state = queue.first()

            val collectedStates = interpreter.step(state)

            val (newFinalStates, nextStates) = collectedStates.partition {
                it.callStack.isEmpty() || it.exceptionRegister != null
            }

            if (state !in nextStates) {
                queue.removeFirst()
            }

            for (nextState in nextStates) {
                if (nextState != state) {
                    queue.add(nextState)
                }
            }
            finalStates.addAll(newFinalStates)
        }

        return finalStates.map { resultModelConverter.convert(it) }
    }

    private fun getInitialState(ctx: UContext, solver: USolverBase<Field<*>, SampleType, Method<*>>, method: Method<*>): ExecutionState =
        ExecutionState(ctx).apply {
            addEntryMethodCall(applicationGraph, method)
            val solverResult = solver.check(UPathConstraints(ctx), useSoftConstraints = true)
            val satResult = solverResult as USatResult<UModelBase<Field<*>, SampleType>>
            val model = satResult.model
            models = persistentListOf(model)
        }
}