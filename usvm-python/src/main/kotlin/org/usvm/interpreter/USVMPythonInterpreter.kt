package org.usvm.interpreter

import org.usvm.*
import org.usvm.interpreter.operations.BadModelException
import org.usvm.interpreter.symbolicobjects.ConverterToPythonObject
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.ObjectValidator
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.SymbolForCPython

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

    private fun getSeeds(state: PythonExecutionState, symbols: List<SymbolForCPython>): List<InterpretedSymbolicPythonObject> =
        symbols.map { interpretSymbolicPythonObject(it.obj, state.pyModel) }

    private fun getConcrete(converter: ConverterToPythonObject, seeds: List<InterpretedSymbolicPythonObject>, symbols: List<SymbolForCPython>): List<PythonObject> =
        (seeds zip symbols).map { (seed, symbol) -> converter.convert(seed, symbol) }

    private fun getInputs(virtualObjects: Collection<PythonObject>, concrete: List<PythonObject?>, seeds: List<InterpretedSymbolicPythonObject>): List<InputObject<PYTHON_OBJECT_REPRESENTATION>>? =
        if (virtualObjects.isEmpty()) {
            val serializedInputs = concrete.map { it!! }.map(pythonObjectSerialization)
            (seeds zip callable.signature zip serializedInputs).map { (p, z) ->
                val (x, y) = p
                InputObject(x, y, z)
            }
        } else {
            null
        }

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> = with(ctx) {
        val concolicRunContext = ConcolicRunContext(state, ctx)
        state.symbolsWithoutConcreteTypes = null
        try {
            val validator = ObjectValidator(concolicRunContext)
            val symbols = state.inputSymbols
            symbols.forEach { validator.check(it.obj) }
            val seeds = getSeeds(state, symbols)
            val converter = ConverterToPythonObject(ctx)
            val concrete = getConcrete(converter, seeds, symbols)
            val virtualObjects = converter.getVirtualObjects()
            val inputs = getInputs(virtualObjects, concrete, seeds)

            /*println("INPUTS:")
            concrete.forEach { println(ConcretePythonInterpreter.getPythonObjectRepr(it)) }
            System.out.flush()*/

            try {
                val result = ConcretePythonInterpreter.concolicRun(
                    namespace,
                    pinnedCallable,
                    concrete,
                    virtualObjects,
                    symbols,
                    concolicRunContext,
                    printErrorMsg
                )
                if (inputs != null) {
                    val serializedResult = pythonObjectSerialization(result)
                    saveRunResult(PythonAnalysisResult(converter, inputs, Success(serializedResult)))
                }

            } catch (_: CPythonExecutionException) {
                if (inputs != null)
                    saveRunResult(PythonAnalysisResult(converter, inputs, Fail()))
            }

            concolicRunContext.curState.wasExecuted = true
            iterationCounter.iterations += 1

            if (concolicRunContext.curState.delayedForks.isEmpty() && inputs == null) {
                concolicRunContext.curState.symbolsWithoutConcreteTypes = converter.getSymbolsWithoutConcreteTypes()
            }

            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.modelDied)

        } catch (_: BadModelException) {

            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.modelDied)
        }
    }
}