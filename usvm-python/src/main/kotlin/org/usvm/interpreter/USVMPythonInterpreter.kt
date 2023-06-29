package org.usvm.interpreter

import org.usvm.*
import org.usvm.language.PythonCallable
import org.usvm.language.PythonProgram

class USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION>(
    private val ctx: UContext,
    private val program: PythonProgram,
    private val callable: PythonCallable,
    private val iterationCounter: IterationCounter,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION,
    private val saveRunResult: (List<PYTHON_OBJECT_REPRESENTATION>, PYTHON_OBJECT_REPRESENTATION?) -> Unit
) : UInterpreter<PythonExecutionState>() {
    private fun prepareNamespace(): PythonNamespace {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, program.asString)
        return namespace
    }

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> =
        with(ctx) {
            val symbols = state.inputSymbols
            val seeds = symbols.map { state.models.first().eval(it.expr) }
            val namespace = prepareNamespace()
            val converter = ConverterToPythonObject(namespace)
            val functionRef = callable.reference(namespace)
            val concrete = (seeds zip callable.signature).map { (seed, type) ->
                converter.convert(seed, type) ?: error("Couldn't construct PythonObject from model")
            }
            val serializedInputs = concrete.map(pythonObjectSerialization)
            val concolicRunContext = ConcolicRunContext(state, ctx)
            val result = ConcretePythonInterpreter.concolicRun(namespace, functionRef, concrete, symbols, concolicRunContext)
            val serializedResult = pythonObjectSerialization(result)
            saveRunResult(serializedInputs, serializedResult)
            concolicRunContext.curState.wasExecuted = true
            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted)
        }
}