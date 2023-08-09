package org.usvm.machine.interpreters

import mu.KLogging
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonProgram
import org.usvm.machine.interpreters.operations.BadModelException
import org.usvm.machine.interpreters.operations.UnregisteredVirtualOperation
import org.usvm.machine.symbolicobjects.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.SymbolForCPython
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.*
import org.usvm.machine.interpreters.operations.myAssertOnState

class USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION>(
    private val ctx: UPythonContext,
    private val typeSystem: PythonTypeSystem,
    program: PythonProgram,
    private val callable: PythonUnpinnedCallable,
    private val iterationCounter: IterationCounter,
    private val printErrorMsg: Boolean,
    private val allowPathDiversion: Boolean = true,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION,
    private val saveRunResult: (PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>) -> Unit
) : UInterpreter<PythonExecutionState>() {
    private val pinnedCallable = program.pinCallable(callable)

    private fun getSeeds(
        concolicRunContext: ConcolicRunContext,
        symbols: List<SymbolForCPython>
    ): List<InterpretedInputSymbolicPythonObject> =
        symbols.map { interpretSymbolicPythonObject(it.obj, concolicRunContext.modelHolder) as InterpretedInputSymbolicPythonObject }

    private fun getConcrete(
        converter: ConverterToPythonObject,
        seeds: List<InterpretedInputSymbolicPythonObject>,
        symbols: List<SymbolForCPython>
    ): List<PythonObject> =
        (seeds zip symbols).map { (seed, _) -> converter.convert(seed) }

    private fun getInputs(
        converter: ConverterToPythonObject,
        concrete: List<PythonObject?>,
        seeds: List<InterpretedInputSymbolicPythonObject>
    ): List<InputObject<PYTHON_OBJECT_REPRESENTATION>>? =
        if (converter.numberOfVirtualObjectUsages() == 0) {
            val serializedInputs = concrete.map { it!! }.map(pythonObjectSerialization)
            (seeds zip callable.signature zip serializedInputs).map { (p, z) ->
                val (x, y) = p
                InputObject(x, y, z)
            }
        } else {
            null
        }

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> = with(ctx) {
        val modelHolder =
            if (state.meta.lastConverter != null)
                state.meta.lastConverter!!.modelHolder
            else
                PyModelHolder(state.pyModel)
        val concolicRunContext =
            ConcolicRunContext(state, ctx, modelHolder, typeSystem, allowPathDiversion)
        state.meta.objectsWithoutConcreteTypes = null
        state.meta.lastConverter?.restart()
        try {
            logger.debug("Step on state: {}", state)
            logger.debug("Source of the state: {}", state.meta.generatedFrom)
            val validator = ObjectValidator(concolicRunContext)
            val symbols = state.inputSymbols
            symbols.forEach { validator.check(it.obj) }
            val seeds = getSeeds(concolicRunContext, symbols)
            val converter = concolicRunContext.converter
            val concrete = getConcrete(converter, seeds, symbols)
            val virtualObjects = converter.getPythonVirtualObjects()
            val inputs = getInputs(converter, concrete, seeds)

            if (logger.isDebugEnabled) {  // getting __repr__ might be slow
                logger.debug(
                    "Generated inputs: {}",
                    concrete.joinToString(", ") { ConcretePythonInterpreter.getPythonObjectRepr(it) }
                )
            }

            try {
                val result = ConcretePythonInterpreter.concolicRun(
                    pinnedCallable.asPythonObject,
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
                logger.debug("Step result: Successful run. Returned ${ConcretePythonInterpreter.getPythonObjectRepr(result)}")

            } catch (exception: CPythonExecutionException) {
                require(exception.pythonExceptionValue != null && exception.pythonExceptionType != null)
                if (ConcretePythonInterpreter.isJavaException(exception.pythonExceptionValue)) {
                    throw ConcretePythonInterpreter.extractException(exception.pythonExceptionValue)
                }
                logger.debug(
                    "Step result: exception from CPython: {} - {}",
                    ConcretePythonInterpreter.getNameOfPythonType(exception.pythonExceptionType),
                    ConcretePythonInterpreter.getPythonObjectRepr(exception.pythonExceptionValue)
                )
                if (inputs != null) {
                    val serializedException = pythonObjectSerialization(exception.pythonExceptionType)
                    saveRunResult(PythonAnalysisResult(converter, inputs, Fail(serializedException)))
                }
            }

            iterationCounter.iterations += 1
            val resultState = concolicRunContext.curState
            if (resultState != null) {
                resultState.meta.wasExecuted = true

                if (resultState.delayedForks.isEmpty() && inputs == null) {
                    var newResultState = resultState
                    concolicRunContext.delayedNonNullObjects.forEach { obj ->
                        newResultState = newResultState?.let {
                            myAssertOnState(it, ctx.mkNot(ctx.mkHeapRefEq(obj.address, ctx.nullRef)))
                        }
                    }
                    if (newResultState == null) {
                        logger.debug("Error in concretization of virtual objects")
                        return StepResult(emptySequence(), false)
                    }
                    require(newResultState == resultState)
                    resultState.meta.objectsWithoutConcreteTypes = converter.getUSVMVirtualObjects()
                    resultState.meta.lastConverter = converter
                    converter.modelHolder.model = resultState.pyModel
                }
                logger.debug("Finished step on state: {}", concolicRunContext.curState)

                return StepResult(concolicRunContext.forkedStates.asSequence(), !state.meta.modelDied)

            } else {
                logger.debug("Ended step with path diversion")
                return StepResult(emptySequence(), false)
            }

        } catch (_: BadModelException) {

            iterationCounter.iterations += 1
            logger.debug("Step result: Bad model")
            return StepResult(concolicRunContext.forkedStates.asSequence(), !state.meta.modelDied)

        } catch (_: UnregisteredVirtualOperation) {

            iterationCounter.iterations += 1
            logger.debug("Step result: Unregistrered virtual operation")
            return StepResult(emptySequence(), false)

        }
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}