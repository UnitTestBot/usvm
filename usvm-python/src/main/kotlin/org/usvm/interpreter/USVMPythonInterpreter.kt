package org.usvm.interpreter

import org.usvm.*
import org.usvm.language.PythonType
import org.usvm.language.PythonUnpinnedCallable

class USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION>(
    private val ctx: UContext,
    private val namespace: PythonNamespace,
    private val callable: PythonUnpinnedCallable,
    private val iterationCounter: IterationCounter,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION,
    private val saveRunResult: (PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>) -> Unit
) : UInterpreter<PythonExecutionState>() {
    private val pinnedCallable = callable.reference(namespace)

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> =
        with(ctx) {
            val symbols = state.inputSymbols
            val seeds = symbols.map { state.models.first().eval(it.expr) }
            val converter = ConverterToPythonObject(namespace)
            val concrete = (seeds zip callable.signature).map { (seed, type) ->
                converter.convert(seed, type) ?: error("Couldn't construct PythonObject from model")
            }
            val serializedInputs = concrete.map(pythonObjectSerialization)
            val concolicRunContext = ConcolicRunContext(state, ctx)
            val result = ConcretePythonInterpreter.concolicRun(namespace, pinnedCallable, concrete, symbols, concolicRunContext)
            val serializedResult = pythonObjectSerialization(result)
            saveRunResult(
                PythonAnalysisResult(
                    (seeds zip callable.signature zip serializedInputs).map { (p, z) ->
                        val (x, y) = p
                        InputObject(x, y, z)
                    },
                    serializedResult
                )
            )
            concolicRunContext.curState.wasExecuted = true
            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted)
        }
}

data class InputObject<PYTHON_OBJECT_REPRESENTATION>(
    val asUExpr: UExpr<*>,
    val type: PythonType,
    val reprFromPythonObject: PYTHON_OBJECT_REPRESENTATION
)

data class PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>(
    val inputValues: List<InputObject<PYTHON_OBJECT_REPRESENTATION>>,
    val result: PYTHON_OBJECT_REPRESENTATION?
)