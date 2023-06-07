package org.usvm.interpreter

import io.ksmt.expr.KBitVec32Value
import org.usvm.*
import org.usvm.language.Callable

class USVMPythonInterpreter(
    private val ctx: UContext,
    private val namespace: PythonNamespace,
    private val callable: Callable,
    private val iterationCounter: IterationCounter
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
                println("CONCRETE: $it")
                val repr = it.toString() //if (it.isTrue) "True" else "False"
                ConcretePythonInterpreter.eval(namespace, repr)
            }
            val concolicRunContext = ConcolicRunContext(state, ctx)
            ConcretePythonInterpreter.concolicRun(namespace, functionRef, concrete, symbols, concolicRunContext)
            concolicRunContext.curState.wasExecuted = true
            //println("Finished with state: ${concolicRunContext.curState}. ${concolicRunContext.curState.pathConstraints.logicalConstraints}")
            //println("Forked states: ${concolicRunContext.forkedStates}")
            //println("Result of step: ${result.forkedStates.take(10).toList()}")
            println("Number of forks: ${concolicRunContext.forkedStates.size}")
            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted)
        }
}