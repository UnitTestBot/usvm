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
            val scope = PythonStepScope(ctx, state)
            val registers = List(callable.numberOfArguments) { mkRegisterReading(it, boolSort) }
            val seeds = (state.models zip registers).map { (model, register) -> model.eval(register) }
            val concrete = seeds.map {
                println("CONCRETE: $it")
                val repr = if (it.isTrue) "True" else "False"
                ConcretePythonInterpreter.eval(namespace, repr)
            }
            ConcretePythonInterpreter.concolicRun(namespace, functionRef, concrete, registers, scope, ctx)
            scope.doWithState { wasExecuted = true }
            // scope.fork(registers.first() ge mkIntNum(0))
            scope.stepResult()
        }
}