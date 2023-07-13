package org.usvm.interpreter

import org.usvm.*
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.language.PythonUnpinnedCallable

class USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION>(
    private val ctx: UContext,
    private val namespace: PythonNamespace,
    private val callable: PythonUnpinnedCallable,
    private val iterationCounter: IterationCounter,
    private val printErrorMsg: Boolean,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION,
    private val saveRunResult: (PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>) -> Unit
) : UInterpreter<PythonExecutionState>() {
    private val pinnedCallable = callable.reference(namespace)

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> =
        with(ctx) {
            val symbols = state.inputSymbols
            val seeds = symbols.map { interpretSymbolicPythonObject(it.obj, state.pyModel) }
            val converter = ConverterToPythonObject(ctx)
            val concrete = seeds.map { seed ->
                converter.convert(seed) ?: error("Couldn't construct PythonObject from model")
            }
            val serializedInputs = concrete.map(pythonObjectSerialization)
            val inputForResult =
                (seeds zip callable.signature zip serializedInputs).map { (p, z) ->
                    val (x, y) = p
                    InputObject(x, y, z)
                }
            val concolicRunContext = ConcolicRunContext(state, ctx)
            try {
                val result = ConcretePythonInterpreter.concolicRun(
                    namespace,
                    pinnedCallable,
                    concrete,
                    symbols,
                    concolicRunContext,
                    printErrorMsg
                )
                val serializedResult = pythonObjectSerialization(result)
                saveRunResult(PythonAnalysisResult(converter, inputForResult, Success(serializedResult)))

            } catch (_: CPythonExecutionException) {
                saveRunResult(PythonAnalysisResult(converter, inputForResult, Fail()))
            }

            concolicRunContext.curState.wasExecuted = true
            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted)
        }
}