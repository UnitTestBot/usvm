package org.usvm.machine.interpreters.symbolic

import mu.KLogging
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonPinnedCallable
import org.usvm.machine.symbolicobjects.*
import org.usvm.language.SymbolForCPython
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.*
import org.usvm.machine.interpreters.concrete.CPythonExecutionException
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PythonObject
import org.usvm.machine.interpreters.symbolic.operations.basic.BadModelException
import org.usvm.machine.interpreters.symbolic.operations.basic.UnregisteredVirtualOperation
import org.usvm.machine.interpreters.symbolic.operations.tracing.CancelledExecutionException
import org.usvm.machine.interpreters.symbolic.operations.tracing.InstructionLimitExceededException
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.rendering.LengthOverflowException
import org.usvm.machine.rendering.PyObjectModelBuilder
import org.usvm.machine.rendering.PythonObjectRenderer
import org.usvm.machine.results.*
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import org.usvm.python.model.*

class USVMPythonInterpreter<PyObjectRepr>(
    private val ctx: PyContext,
    private val typeSystem: PythonTypeSystem,
    private val pinnedCallable: PythonPinnedCallable,
    private val printErrorMsg: Boolean,
    private val statistics: PythonMachineStatisticsOnFunction,
    private val maxInstructions: Int,
    private val resultsReceiver: PyMachineResultsReceiver<PyObjectRepr>,
    private val isCancelled: (Long) -> Boolean,
    private val allowPathDiversion: Boolean = true,
) : UInterpreter<PyState>() {
    override fun step(state: PyState): StepResult<PyState> {
        val modelHolder = PyModelHolder(state.pyModel)
        val concolicRunContext = constructConcolicRunContext(state, modelHolder)
        state.meta.objectsWithoutConcreteTypes = null
        logger.debug("Step on state: {}", state)
        logger.debug("Source of the state: {}", state.meta.generatedFrom)
        val symbols = state.inputSymbols
        val interpreted = symbols.map { interpretSymbolicPythonObject(concolicRunContext, it) }
        val builder = PyObjectModelBuilder(concolicRunContext, modelHolder)
        val objectModels = interpreted.map { builder.convert(it) }
        val inputModel = PyInputModel(objectModels)
        resultsReceiver.inputModelObserver.onInputModel(inputModel)
        val renderer = PythonObjectRenderer()
        concolicRunContext.builder = builder
        concolicRunContext.renderer = renderer
        val concrete = getConcrete(renderer, objectModels)
        if (concrete == null) {
            state.meta.modelDied = true
            return StepResult(emptySequence(), false)
        }
        val inputRepr = processConcreteInput(concrete, renderer)
        concolicRunContext.usesVirtualInputs = inputRepr == null
        val result: PythonObject? = try {
            ConcretePythonInterpreter.concolicRun(
                pinnedCallable.asPythonObject,
                concrete,
                renderer.getPythonVirtualObjects(),
                symbols.map { SymbolForCPython(it, 0) },
                concolicRunContext,
                printErrorMsg
            )
        } catch (exception: CPythonExecutionException) {
            val realCPythonException = processCPythonExceptionDuringConcolicRun(
                concolicRunContext,
                exception,
                renderer,
                inputModel,
                inputRepr
            )
            if (!realCPythonException) {
                return StepResult(concolicRunContext.forkedStates.asSequence(), !state.isTerminated())
            }
            null
        }
        if (result != null) {
            processSuccessfulExecution(result, inputModel, inputRepr)
        }

        val resultState = concolicRunContext.curState
        return if (resultState != null) {
            resultState.meta.wasExecuted = true
            if (resultState.delayedForks.isEmpty() && inputRepr == null) {
                resultState.meta.objectsWithoutConcreteTypes = renderer.getUSVMVirtualObjects()
            }
            logger.debug("Finished step on state: {}", concolicRunContext.curState)
            StepResult(concolicRunContext.forkedStates.asSequence(), !state.isTerminated())
        } else {
            logger.debug("Ended step with path diversion")
            StepResult(emptySequence(), !state.isTerminated())
        }
    }

    private fun processConcreteInput(
        concrete: List<PythonObject>,
        renderer: PythonObjectRenderer
    ): List<PyObjectRepr>? {
        if (logger.isDebugEnabled) {  // getting __repr__ might be slow
            logger.debug(
                "Generated inputs: {}",
                concrete.joinToString(", ") {
                    ReprObjectSerializer.serialize(it)
                }
            )
        }
        resultsReceiver.inputPythonObjectObserver.onInputObjects(concrete)
        return if (renderer.getPythonVirtualObjects().isNotEmpty()) {
            null
        } else {
            concrete.map { resultsReceiver.serializer.serialize(it) }
        }
    }

    private fun processSuccessfulExecution(
        result: PythonObject,
        inputModel: PyInputModel,
        inputReprs: List<PyObjectRepr>?
    ) {
        if (logger.isDebugEnabled) {
            logger.debug("Step result: Successful run. Returned ${ReprObjectSerializer.serialize(result)}")
        }
        if (inputReprs != null) {
            val resultRepr = resultsReceiver.serializer.serialize(result)
            val test = PyTest(
                inputModel,
                inputReprs,
                PyResultSuccess(resultRepr)
            )
            resultsReceiver.pyTestObserver.onPyTest(test)
        }
    }

    private fun processCPythonExceptionDuringConcolicRun(
        concolicRunContext: ConcolicRunContext,
        exception: CPythonExecutionException,
        renderer: PythonObjectRenderer,
        inputModel: PyInputModel,
        inputReprs: List<PyObjectRepr>?
    ): Boolean {
        require(exception.pythonExceptionType != null)
        require(exception.pythonExceptionValue != null)
        if (ConcretePythonInterpreter.isJavaException(exception.pythonExceptionValue)) {
            when (val javaException = ConcretePythonInterpreter.extractException(exception.pythonExceptionValue)) {
                is UnregisteredVirtualOperation -> processUnregisteredVirtualOperation(concolicRunContext, renderer)
                is BadModelException -> logger.debug("Step result: Bad model")
                is InstructionLimitExceededException -> processInstructionLimitExceeded(concolicRunContext)
                is CancelledExecutionException -> processCancelledException(concolicRunContext)
                else -> throw javaException
            }
            return false
        }
        logger.debug(
            "Step result: exception from CPython: {} - {}",
            ConcretePythonInterpreter.getNameOfPythonType(exception.pythonExceptionType),
            ReprObjectSerializer.serialize(exception.pythonExceptionValue)
        )
        if (inputReprs != null) {
            val resultRepr = resultsReceiver.serializer.serialize(exception.pythonExceptionType)
            val test = PyTest(
                inputModel,
                inputReprs,
                PyResultFailure(resultRepr)
            )
            resultsReceiver.pyTestObserver.onPyTest(test)
        }
        return true
    }

    private fun processUnregisteredVirtualOperation(
        concolicRunContext: ConcolicRunContext,
        renderer: PythonObjectRenderer
    ) {
        logger.debug("Step result: Unregistrered virtual operation")
        val resultState = concolicRunContext.curState
        concolicRunContext.statistics.addUnregisteredVirtualOperation()
        // TODO: make this more accurate
        if (resultState != null && resultState.delayedForks.isEmpty()) {
            resultState.meta.objectsWithoutConcreteTypes = renderer.getUSVMVirtualObjects()
            resultState.meta.wasExecuted = true
        } else if (resultState != null) {
            resultState.meta.modelDied = true
        }
    }

    private fun processInstructionLimitExceeded(concolicRunContext: ConcolicRunContext) {
        logger.debug("Step result: InstructionLimitExceededException")
        concolicRunContext.curState?.meta?.wasInterrupted = true
    }

    private fun processCancelledException(concolicRunContext: ConcolicRunContext) {
        logger.debug("Step result: execution cancelled")
        concolicRunContext.curState?.meta?.wasInterrupted = true
    }

    private fun getConcrete(renderer: PythonObjectRenderer, objectModels: List<PyObjectModel>): List<PythonObject>? {
        try {
            return objectModels.map { renderer.convert(it) }
        } catch (_: LengthOverflowException) {
            logger.warn("Step result: length overflow")
        } catch (_: CPythonExecutionException) {
            logger.info("Step result: could not assemble Python object")
        }
        return null
    }

    private fun constructConcolicRunContext(
        state: PyState,
        modelHolder: PyModelHolder
    ): ConcolicRunContext {
        val start = System.currentTimeMillis()
        return ConcolicRunContext(
            state,
            ctx,
            modelHolder,
            typeSystem,
            allowPathDiversion,
            statistics,
            maxInstructions
        ) {
            isCancelled(start)
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}