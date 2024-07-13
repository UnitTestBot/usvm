package org.usvm.machine.interpreters.symbolic.operations.tracing

import mu.KLogging
import org.usvm.isTrue
import org.usvm.machine.CancelledExecutionException
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.InstructionLimitExceededException
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.symbolicobjects.memory.getToBoolValue
import java.util.concurrent.Callable

private val logger = object : KLogging() {}.logger
class PathDiversionException : RuntimeException()

/**
 * This is a very important method.
 *
 * When we use concolic approach instead of classic symbolic execution,
 * we need to repeat the prefix of symbolically executed actions
 * at each iteration of function `concolicRun`.
 * We just save answers to each query of the concrete interpreter.
 * If we detected some unexpected queries, we notify the engine about path diversion.
 *
 * The method [withTracing] is used to wrap actual symbolic operations.
 * It is supposed to be used in [org.usvm.interpreter.CPythonAdapter].
 * */
fun <T : Any> withTracing(
    context: ConcolicRunContext,
    newEventParameters: SymbolicHandlerEventParameters<T>,
    resultSupplier: Callable<T?>,
): T? {
    if (context.isCancelled.call()) {
        throw CancelledExecutionException()
    }
    context.instructionCounter += 1
    if (context.instructionCounter > context.maxInstructions) {
        throw InstructionLimitExceededException()
    }
    var curState = context.curState ?: return null
    if (newEventParameters is NextInstruction) {
        context.statistics.updateCoverage(newEventParameters, context.usesVirtualInputs)
        curState.uniqueInstructions = curState.uniqueInstructions.add(newEventParameters.pyInstruction)
    }
    if (context.pathPrefix.isEmpty()) {
        val result = runCatching { resultSupplier.call() }.onFailure { System.err.println(it) }.getOrThrow()
        curState = context.curState ?: return null
        val eventRecord = SymbolicHandlerEvent(newEventParameters, result)
        curState.concolicQueries = curState.concolicQueries.add(eventRecord)
        if (newEventParameters is NextInstruction) {
            curState.pathNode += newEventParameters.pyInstruction
        }
        return result
    }
    val event = context.pathPrefix.first()
    context.pathPrefix = context.pathPrefix.drop(1)
    if (event.parameters != newEventParameters) {
        logger.debug("Path diversion!")
        logger.debug("Expected: {}", event.parameters)
        logger.debug("Got: {}", newEventParameters)
        context.pathDiversion()
        return null
    }
    event.result ?: return null

    @Suppress("unchecked_cast")
    return event.result as T
}


fun handlerForkResultKt(context: ConcolicRunContext, cond: SymbolForCPython, result: Boolean) {
    if (context.curState == null) {
        return
    }
    val obj = cond.obj ?: return

    val expectedResult = obj.getToBoolValue(context)?.let {
        context.extractCurState().pyModel.eval(it)
    }?.isTrue ?: return

    if (result != expectedResult) {
        logger.debug("Path diversion after fork!")
        logger.debug("Condition: {}", obj.getToBoolValue(context))
        logger.debug("Expected: {}", expectedResult)
        logger.debug("Got: {}", result)
        context.pathDiversion()
    }
}
