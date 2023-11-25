package org.usvm.machine.interpreters

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonPinnedCallable
import org.usvm.machine.interpreters.operations.basic.BadModelException
import org.usvm.machine.interpreters.operations.basic.UnregisteredVirtualOperation
import org.usvm.machine.symbolicobjects.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.SymbolForCPython
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.*
import org.usvm.machine.interpreters.operations.tracing.CancelledExecutionException
import org.usvm.machine.interpreters.operations.tracing.InstructionLimitExceededException
import org.usvm.machine.model.PyModel
import org.usvm.machine.saving.*
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction

class USVMPythonInterpreter<InputRepr>(
    private val ctx: UPythonContext,
    private val typeSystem: PythonTypeSystem,
    private val unpinnedCallable: PythonUnpinnedCallable,
    private val pinnedCallable: PythonPinnedCallable,
    private val iterationCounter: IterationCounter,
    private val printErrorMsg: Boolean,
    private val statistics: PythonMachineStatisticsOnFunction,
    private val maxInstructions: Int,
    private val saver: PythonAnalysisResultSaver<InputRepr>,
    private val isCancelled: (Long) -> Boolean,
    private val allowPathDiversion: Boolean = true,
) : UInterpreter<PythonExecutionState>() {
    private fun getSeeds(
        concolicRunContext: ConcolicRunContext,
        symbols: List<UninterpretedSymbolicPythonObject>
    ): List<InterpretedSymbolicPythonObject> =
        symbols.map { interpretSymbolicPythonObject(concolicRunContext, it) }

    @Suppress("unused_parameter")
    private fun getConcrete(
        concolicRunContext: ConcolicRunContext,
        visitor: PreConversionVisitor,
        converter: ConverterToPythonObject,
        seeds: List<InterpretedSymbolicPythonObject>,
        symbols: List<UninterpretedSymbolicPythonObject>
    ): List<PythonObject> {
        /*(seeds zip symbols).forEach { (seed, symbol) ->
            visitor.visit(seed, symbol, concolicRunContext)
        }*/
        return seeds.map {
            converter.convert(it)
        }
    }

    private fun getInputs(
        converter: ConverterToPythonObject,
        concrete: List<PythonObject>,
        seeds: List<InterpretedSymbolicPythonObject>
    ): List<GeneratedPythonObject>? =
        if (converter.numberOfVirtualObjectUsages() == 0) {
            (seeds zip unpinnedCallable.signature zip concrete).map { (p, ref) ->
                val (asUExpr, type) = p
                GeneratedPythonObject(ref, type, asUExpr)
            }
        } else {
            null
        }

    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> = runBlocking {
        val modelHolder =
            if (state.meta.lastConverter != null)
                state.meta.lastConverter!!.modelHolder
            else
                PyModelHolder(state.pyModel)
        require(modelHolder.model == state.pyModel) { "Bad model inside modelHolder!" }
        val start = System.currentTimeMillis()
        val concolicRunContext =
            ConcolicRunContext(
                state,
                ctx,
                modelHolder,
                typeSystem,
                allowPathDiversion,
                statistics,
                maxInstructions
            ) {
                isCancelled(start)
                // timeoutPerRunMs?.let { System.currentTimeMillis() - start > timeoutPerRunMs } ?: false
            }
        state.meta.objectsWithoutConcreteTypes = null
        state.meta.lastConverter?.restart()
        try {
            logger.debug("Step on state: {}", state)
            logger.debug("Source of the state: {}", state.meta.generatedFrom)
            val symbols = state.inputSymbols
            val seeds = getSeeds(concolicRunContext, symbols)
            val converter = concolicRunContext.converter
            state.meta.lastConverter = null
            val visitor = PreConversionVisitor(ctx, state.memory, typeSystem, modelHolder, state.preAllocatedObjects)
            val concrete = try {
                getConcrete(concolicRunContext, visitor, converter, seeds, symbols)
            } catch (_: LengthOverflowException) {
                logger.warn("Step result: length overflow")
                state.meta.modelDied = true
                return@runBlocking StepResult(emptySequence(), false)
            } catch (_: CPythonExecutionException) {
                logger.info("Step result: could not assemble Python object")
                state.meta.modelDied = true
                return@runBlocking StepResult(emptySequence(), false)
            }
            state.pyModel.uModel.preallocatedObjects.listAllocatedStrs().forEach {
                require(state.preAllocatedObjects.concreteString(it) != null) {
                    "State's preallocated objects must include models' preallocated object"
                }
            }
            // logger.debug("Finished constructing")
            val virtualObjects = converter.getPythonVirtualObjects()
            val madeInputSerialization: Boolean = runCatching {
                getInputs(converter, concrete, seeds)
            }.getOrElse {
                logger.debug(
                    "Error while serializing inputs. Types: {}. Omitting step...",
                    seeds.map { it.getConcreteType() }
                )
                return@runBlocking StepResult(emptySequence(), false)
            }?.let {
                // println("Getting representation")
                // System.out.flush()
                val representation = saver.serializeInput(it, converter)
                // println("Finished getting representation")
                // System.out.flush()
                launch {
                    saver.saveNextInputs(representation)
                }
                true
            } ?: false
            concolicRunContext.usesVirtualInputs = !madeInputSerialization

            if (logger.isDebugEnabled) {  // getting __repr__ might be slow
                logger.debug(
                    "Generated inputs: {}",
                    concrete.joinToString(", ") {
                        ReprObjectSerializer.serialize(it)
                    }
                )
            }

            try {
                val result = ConcretePythonInterpreter.concolicRun(
                    pinnedCallable.asPythonObject,
                    concrete,
                    virtualObjects,
                    symbols.map { SymbolForCPython(it, 0) },
                    concolicRunContext,
                    printErrorMsg
                )
                if (madeInputSerialization) {
                    saver.saveExecutionResult(Success(result))
                }
                if (logger.isDebugEnabled) {
                    logger.debug("Step result: Successful run. Returned ${ReprObjectSerializer.serialize(result)}")
                }

            } catch (exception: CPythonExecutionException) {
                require(exception.pythonExceptionType != null)
                require(exception.pythonExceptionValue != null)
                if (ConcretePythonInterpreter.isJavaException(exception.pythonExceptionValue)) {
                    val numberOfVirtualObjects = converter.getUSVMVirtualObjects().size
                    (concolicRunContext.forkedStates + state).forEach {
                        it.meta.numberOfVirtualObjectsInParent = numberOfVirtualObjects
                    }
                    when (val javaException = ConcretePythonInterpreter.extractException(exception.pythonExceptionValue)) {
                        UnregisteredVirtualOperation -> {
                            iterationCounter.iterations += 1
                            logger.debug("Step result: Unregistrered virtual operation")
                            val resultState = concolicRunContext.curState
                            concolicRunContext.statistics.addUnregisteredVirtualOperation()
                            require(!madeInputSerialization)
                            if (resultState != null && resultState.delayedForks.isEmpty()) {
                                resultState.meta.objectsWithoutConcreteTypes = converter.getUSVMVirtualObjects()
                                resultState.meta.lastConverter = converter
                                resultState.meta.wasExecuted = true
                            }
                            return@runBlocking StepResult(concolicRunContext.forkedStates.asSequence(), !state.isTerminated())
                        }
                        BadModelException -> {
                            iterationCounter.iterations += 1
                            logger.debug("Step result: Bad model")
                            return@runBlocking StepResult(concolicRunContext.forkedStates.asSequence(), !state.isTerminated())
                        }
                        InstructionLimitExceededException -> {
                            iterationCounter.iterations += 1
                            logger.debug("Step result: InstructionLimitExceededException")
                            concolicRunContext.curState?.meta?.wasInterrupted = true
                            return@runBlocking StepResult(concolicRunContext.forkedStates.reversed().asSequence(), !state.isTerminated())
                        }
                        CancelledExecutionException -> {
                            logger.debug("Step result: execution cancelled")
                            concolicRunContext.curState?.meta?.wasInterrupted = true
                            return@runBlocking StepResult(concolicRunContext.forkedStates.reversed().asSequence(), !state.isTerminated())
                        }
                        else -> throw  javaException
                    }
                }
                val nameOfException = ConcretePythonInterpreter.getNameOfPythonType(exception.pythonExceptionType)
                val resultState = concolicRunContext.curState
                if ((nameOfException == "AttributeError" || nameOfException == "TypeError") && resultState != null) {
                    resultState.meta.endedWithTypeErrorOrAttributeError = true
                }
                logger.debug(
                    "Step result: exception from CPython: {} - {}",
                    nameOfException,
                    ReprObjectSerializer.serialize(exception.pythonExceptionValue)
                )
                if (madeInputSerialization) {
                    // println("Saving result")
                    // System.out.flush()
                    saver.saveExecutionResult(Fail(exception.pythonExceptionType))
                    // println("Finished saving result")
                    // System.out.flush()
                }
            }

            iterationCounter.iterations += 1
            val resultState = concolicRunContext.curState
            if (resultState != null) {
                resultState.meta.wasExecuted = true

                if (resultState.delayedForks.isEmpty() && !madeInputSerialization) {
                    resultState.meta.objectsWithoutConcreteTypes = converter.getUSVMVirtualObjects()
                    resultState.meta.lastConverter = converter
                }
                val numberOfVirtualObjects = converter.getUSVMVirtualObjects().size
                logger.debug("Finished step on state: {}", concolicRunContext.curState)
                (concolicRunContext.forkedStates + state).forEach {
                    it.meta.numberOfVirtualObjectsInParent = numberOfVirtualObjects
                    if (resultState.meta.endedWithTypeErrorOrAttributeError) {
                        it.meta.parentEndedWithTypeOrAttributeError = true
                    }
                }
                return@runBlocking StepResult(concolicRunContext.forkedStates.asSequence(), !state.isTerminated())

            } else {
                logger.debug("Ended step with path diversion")
                return@runBlocking StepResult(emptySequence(), !state.isTerminated())
            }

        } finally {
            concolicRunContext.converter.restart()
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}