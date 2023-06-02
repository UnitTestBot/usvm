package org.usvm.interpreter

import org.usvm.*
import org.usvm.language.Callable
import org.usvm.language.PythonType

typealias PythonStepScope = StepScope<PythonExecutionState, PythonType>

class USVMPythonInterpreter(
    private val ctx: UContext,
    private val namespace: PythonNamespace,
    private val callable: Callable
) : UInterpreter<PythonExecutionState>() {
    private val functionRef = callable.reference(namespace)
    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> =
        with(ctx) {
            println("Step on $state")
            println(state.pathConstraints.logicalConstraints)
            System.out.flush()
            val symbols = state.symbols
            val seeds = symbols.map { state.models.first().eval(it) }
            val concrete = seeds.map {
                println("CONCRETE: $it")
                val repr = it.toString() //if (it.isTrue) "True" else "False"
                ConcretePythonInterpreter.eval(namespace, repr)
            }
            val scope = PythonStepScope(ctx, state)
            ConcretePythonInterpreter.concolicRun(namespace, functionRef, concrete, symbols, scope, ctx)
            scope.doWithState { wasExecuted = true }
            // scope.fork(registers.first() ge mkIntNum(0))
            val result = scope.stepResult()
            println("Result of step: ${result.forkedStates.take(10).toList()}")
            result
        }
}