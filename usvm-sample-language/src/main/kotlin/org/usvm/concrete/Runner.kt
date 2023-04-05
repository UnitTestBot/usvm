package org.usvm.concrete

import org.ksmt.solver.z3.KZ3Solver
import org.usvm.concrete.interpreter.SampleInterpreter
import org.usvm.concrete.state.ExecutionState
import org.usvm.concrete.state.addEntryMethodCall
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.*

class Runner(
    val program: Program,
    val maxStates: Int = 10,
) {
    private val applicationGraph = DefaultSampleApplicationGraph(program)
    private val ctx = UContext()
    private val typeSystem = SampleTypeSystem()

    fun run(method: Method<*>): List<ProgramExecutionResult> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field<*>, SampleType, Method<*>>(ctx)
        val solver = USolverBase(ctx, KZ3Solver(ctx), translator, decoder)

        val resultModelConverter = ResultModelConverter(ctx, method)
        val initialState = getInitialState(solver, method)

        val interpreter = SampleInterpreter(ctx, applicationGraph, solver)

        val queue = ArrayDeque<ExecutionState>()
        queue.add(initialState)

        val finalStates = mutableListOf<ExecutionState>()

        while (queue.isNotEmpty() && finalStates.size < maxStates) {
            val state = queue.first()

            val collectedStates = interpreter.step(state)

            val (newFinalStates, nextStates) = collectedStates.partition {
                it.callStack.isEmpty() || it.exceptionRegister != null
            }

            if (state in newFinalStates) {
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

    private fun getInitialState(solver: USolverBase<Field<*>, SampleType, Method<*>>, method: Method<*>): ExecutionState =
        ExecutionState(ctx, typeSystem).apply {
            addEntryMethodCall(applicationGraph, method)
            val model = (solver.check(memory, UPathConstraintsSet(ctx.trueExpr)) as USolverSat<UModelBase<Field<*>, SampleType>>).model
            models = persistentListOf(model)
        }
}