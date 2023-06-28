package org.usvm.interpreter

import org.usvm.*
import org.usvm.language.PythonCallable
import org.usvm.language.PythonProgram

class USVMPythonInterpreter(
    private val ctx: UContext,
    private val program: PythonProgram,
    private val callable: PythonCallable,
    private val iterationCounter: IterationCounter
) : UInterpreter<PythonExecutionState>() {
    private fun prepareNamespace(): PythonNamespace {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, program.asString)
        return namespace
    }

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> =
        with(ctx) {
            //println("Step on $state. ${state.wasExecuted}. Executed path: ${state.path}")
            //println(state.pathConstraints.logicalConstraints)
            //System.out.flush()
            val symbols = state.inputSymbols
            val seeds = symbols.map { state.models.first().eval(it.expr) }
            val namespace = prepareNamespace()
            val converter = ConverterToPythonObject(namespace)
            val functionRef = callable.reference(namespace)
            val concrete = (seeds zip callable.signature).map { (seed, type) ->
                //println("Concrete: $seed")
                //System.out.flush()
                converter.convert(seed, type) ?: error("Couldn't construct PythonObject from model")
            }
            val concolicRunContext = ConcolicRunContext(state, ctx)
            val result = ConcretePythonInterpreter.concolicRun(namespace, functionRef, concrete, symbols, concolicRunContext)
            concolicRunContext.curState.wasExecuted = true
            ConcretePythonInterpreter.printPythonObject(result)
            //println("Finished with state: ${concolicRunContext.curState}. ${concolicRunContext.curState.pathConstraints.logicalConstraints}")
            //println("Forked states: ${concolicRunContext.forkedStates}")
            //println("Result of step: ${result.forkedStates.take(10).toList()}")
            //println("Number of forks: ${concolicRunContext.forkedStates.size}")
            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted)
        }
}