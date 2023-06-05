package org.usvm.interpreter

import org.usvm.*
import org.usvm.language.Callable

class USVMPythonInterpreter(
    private val ctx: UContext,
    private val namespace: PythonNamespace,
    private val callable: Callable
) : UInterpreter<PythonExecutionState>() {
    private val functionRef = callable.reference(namespace)
    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> =
        with(ctx) {
            //println("Step on $state. ${state.wasExecuted}. Executed path: ${state.path}")
            //println(state.pathConstraints.logicalConstraints)
            //System.out.flush()
            val symbols = List(callable.numberOfArguments) { state.memory.read(URegisterRef(ctx.intSort, it)) }
            val seeds = symbols.map { state.models.first().eval(it) }
            val concrete = seeds.map {
                //println("CONCRETE: $it")
                val repr = it.toString() //if (it.isTrue) "True" else "False"
                ConcretePythonInterpreter.eval(namespace, repr)
            }
            val concolicRunContext = ConcolicRunContext(state, ctx)
            ConcretePythonInterpreter.concolicRun(namespace, functionRef, concrete, symbols, concolicRunContext)
            concolicRunContext.curState.wasExecuted = true
            //println("Finished with state: ${concolicRunContext.curState}. ${concolicRunContext.curState.pathConstraints.logicalConstraints}")
            //println("Forked states: ${concolicRunContext.forkedStates}")
            //println("Result of step: ${result.forkedStates.take(10).toList()}")
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted)
        }
}