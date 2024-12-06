package org.usvm.machine.interpreters.symbolic

import mu.KLogging
import org.usvm.StepResult
import org.usvm.UInterpreter
import org.usvm.language.PyPinnedCallable
import org.usvm.machine.BadModelException
import org.usvm.machine.CPythonExecutionException
import org.usvm.machine.CancelledExecutionException
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.InstructionLimitExceededException
import org.usvm.machine.PyContext
import org.usvm.machine.PyExecutionException
import org.usvm.machine.PyExecutionExceptionFromJava
import org.usvm.machine.PyState
import org.usvm.machine.UnregisteredVirtualOperation
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.results.PyMachineResultsReceiver
import org.usvm.machine.results.serialization.ReprObjectSerializer
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.rendering.LengthOverflowException
import org.usvm.machine.symbolicobjects.rendering.PyValueBuilder
import org.usvm.machine.symbolicobjects.rendering.PyValueRenderer
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import org.usvm.python.model.PyInputModel
import org.usvm.python.model.PyResultFailure
import org.usvm.python.model.PyResultSuccess
import org.usvm.python.model.PyTest
import org.usvm.python.model.PyValue

class USVMPythonInterpreter<PyObjectRepr>(
    private val ctx: PyContext,
    private val pinnedCallable: PyPinnedCallable,
    private val printErrorMsg: Boolean,
    private val statistics: PythonMachineStatisticsOnFunction,
    private val maxInstructions: Int,
    private val resultsReceiver: PyMachineResultsReceiver<PyObjectRepr>,
    private val isCancelled: (Long) -> Boolean,
    private val allowPathDiversion: Boolean = true,
) : UInterpreter<PyState>() {
    override fun step(state: PyState): StepResult<PyState> {
        state.pathNodeBreakpoints = state.pathNodeBreakpoints.add(state.pathNode)

        val modelHolder = PyModelHolder(state.pyModel)
        val concolicRunContext = constructConcolicRunContext(state, modelHolder)
        val renderer = concolicRunContext.renderer
        state.objectsWithoutConcreteTypes = null
        logger.debug("Step on state: {}", state)
        logger.debug("Source of the state: {}", state.generatedFrom)

        val symbols = state.inputSymbols
        val interpreted = symbols.map { interpretSymbolicPythonObject(concolicRunContext, it) }
        val objectModels = try {
            interpreted.map { concolicRunContext.builder.convert(it) }
        } catch (_: LengthOverflowException) {
            logger.warn("LengthOverflowException occurred")
            state.modelDied = true
            return StepResult(emptySequence(), false)
        }

        val inputModel = PyInputModel(objectModels)
        resultsReceiver.inputModelObserver.onInputModel(inputModel)

        val concrete = getConcrete(renderer, objectModels)
        if (concrete == null) {
            state.modelDied = true
            return StepResult(emptySequence(), false)
        }

        val inputRepr = processConcreteInput(concrete, renderer)
        concolicRunContext.usesVirtualInputs = inputRepr == null

        val result: PyObject? = try {
            ConcretePythonInterpreter.concolicRun(
                pinnedCallable.pyObject,
                concrete,
                renderer.getPythonVirtualObjects(),
                symbols.map { SymbolForCPython(it, 0) },
                concolicRunContext,
                printErrorMsg
            )
        } catch (exception: PyExecutionException) {
            when (exception) {
                is CPythonExecutionException -> {
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
                }
                is PyExecutionExceptionFromJava -> {
                    processJavaException(concolicRunContext, exception, renderer)
                }
            }
            null
        }

        if (result != null) {
            processSuccessfulExecution(result, inputModel, inputRepr)
        }

        val resultState = concolicRunContext.curState
        return if (resultState != null) {
            resultState.wasExecuted = true
            if (resultState.delayedForks.isEmpty() && inputRepr == null) {
                resultState.objectsWithoutConcreteTypes = renderer.getUSVMVirtualObjects()
            }
            logger.debug("Finished step on state: {}", concolicRunContext.curState)
            StepResult(concolicRunContext.forkedStates.asSequence(), !state.isTerminated())
        } else {
            logger.debug("Ended step with path diversion")
            StepResult(emptySequence(), !state.isTerminated())
        }
    }

    private fun processConcreteInput(
        concrete: List<PyObject>,
        renderer: PyValueRenderer,
    ): List<PyObjectRepr>? {
        logger.debug {
            concrete.joinToString(prefix = "Generated inputs: {", postfix = "}", separator = ", ") {
                ReprObjectSerializer.serialize(it)
            }
        }
        resultsReceiver.inputPythonObjectObserver.onInputObjects(concrete)
        return if (renderer.getPythonVirtualObjects().isNotEmpty()) {
            null
        } else {
            concrete.map { resultsReceiver.serializer.serialize(it) }
        }
    }

    private fun processSuccessfulExecution(
        result: PyObject,
        inputModel: PyInputModel,
        inputReprs: List<PyObjectRepr>?,
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

    private fun processJavaException(
        concolicRunContext: ConcolicRunContext,
        exception: PyExecutionExceptionFromJava,
        renderer: PyValueRenderer,
    ) {
        when (exception) {
            is UnregisteredVirtualOperation -> processUnregisteredVirtualOperation(concolicRunContext, renderer)
            is BadModelException -> logger.debug("Step result: Bad model")
            is InstructionLimitExceededException -> processInstructionLimitExceeded(concolicRunContext)
            is CancelledExecutionException -> processCancelledException(concolicRunContext)
        }
    }

    private fun processCPythonExceptionDuringConcolicRun(
        concolicRunContext: ConcolicRunContext,
        exception: CPythonExecutionException,
        renderer: PyValueRenderer,
        inputModel: PyInputModel,
        inputReprs: List<PyObjectRepr>?,
    ): Boolean {
        requireNotNull(exception.pythonExceptionType)
        requireNotNull(exception.pythonExceptionValue)
        if (ConcretePythonInterpreter.isJavaException(exception.pythonExceptionValue)) {
            val javaException = ConcretePythonInterpreter.extractException(exception.pythonExceptionValue)
            if (javaException is PyExecutionExceptionFromJava) {
                processJavaException(concolicRunContext, javaException, renderer)
            } else {
                throw javaException
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
        renderer: PyValueRenderer,
    ) {
        logger.debug("Step result: Unregistrered virtual operation")
        val resultState = concolicRunContext.curState
        concolicRunContext.statistics.addUnregisteredVirtualOperation()
        // TODO: make this more accurate
        if (resultState != null && resultState.delayedForks.isEmpty()) {
            resultState.objectsWithoutConcreteTypes = renderer.getUSVMVirtualObjects()
            resultState.wasExecuted = true
        } else if (resultState != null) {
            resultState.modelDied = true
        }
    }

    private fun processInstructionLimitExceeded(concolicRunContext: ConcolicRunContext) {
        logger.debug("Step result: InstructionLimitExceededException")
        concolicRunContext.curState?.wasInterrupted = true
    }

    private fun processCancelledException(concolicRunContext: ConcolicRunContext) {
        logger.debug("Step result: execution cancelled")
        concolicRunContext.curState?.wasInterrupted = true
    }

    private fun getConcrete(renderer: PyValueRenderer, objectModels: List<PyValue>): List<PyObject>? {
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
        modelHolder: PyModelHolder,
    ): ConcolicRunContext {
        val start = System.currentTimeMillis()
        val builder = PyValueBuilder(state, modelHolder)
        val renderer = PyValueRenderer()
        return ConcolicRunContext(
            state,
            ctx,
            modelHolder,
            allowPathDiversion,
            statistics,
            maxInstructions,
            builder,
            renderer
        ) {
            isCancelled(start)
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
