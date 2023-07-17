package org.usvm.interpreter

import org.usvm.*
import org.usvm.interpreter.operations.BadModelException
import org.usvm.interpreter.operations.myFork
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject

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

    private fun getConcrete(converter: ConverterToPythonObject, seeds: List<InterpretedSymbolicPythonObject>): List<PythonObject?> =
        seeds.map { seed -> converter.convert(seed) }

    private fun getVirtual(concrete: List<PythonObject?>, seeds: List<InterpretedSymbolicPythonObject>, symbols: List<SymbolForCPython>): List<VirtualPythonObject?> =
        (concrete zip seeds zip symbols).map { (p, symbol) ->
            val (pythonObject, interpretedObject) = p
            if (pythonObject != null)
                null
            else
                VirtualPythonObject(interpretedObject, symbol)
        }

    private fun getInputs(virtualObjects: List<VirtualPythonObject?>, concrete: List<PythonObject?>, seeds: List<InterpretedSymbolicPythonObject>): List<InputObject<PYTHON_OBJECT_REPRESENTATION>>? =
        if (virtualObjects.all { it == null }) {
            val serializedInputs = concrete.map { it!! }.map(pythonObjectSerialization)
            (seeds zip callable.signature zip serializedInputs).map { (p, z) ->
                val (x, y) = p
                InputObject(x, y, z)
            }
        } else {
            null
        }

    private fun forkOnVirtualObjects(ctx: ConcolicRunContext, virtualObjects: List<VirtualPythonObject?>): Boolean {
        val obj = virtualObjects.firstNotNullOfOrNull { it } ?: return false
        val stream = ctx.curState.pyModel.uModel.types.typeStream(obj.obj.address)
        if (stream.isEmpty)
            return false
        val candidate = stream.take(1).first()
        myFork(ctx, ctx.curState.pathConstraints.typeConstraints.evalIs(obj.symbol.obj.address, candidate))
        return true
    }

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> = with(ctx) {
        val concolicRunContext = ConcolicRunContext(state, ctx)
        try {
            val symbols = state.inputSymbols
            val seeds = getSeeds(state, symbols)
            val converter = ConverterToPythonObject(ctx)
            val concrete = getConcrete(converter, seeds)
            val virtualObjects = getVirtual(concrete, seeds, symbols)
            val inputs = getInputs(virtualObjects, concrete, seeds)
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

            val madeFork = forkOnVirtualObjects(concolicRunContext, virtualObjects)
            concolicRunContext.curState.wasExecuted = !madeFork
            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted && !state.modelDied)

        } catch (_: BadModelException) {

            iterationCounter.iterations += 1
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.wasExecuted && !state.modelDied)
        }
    }
}