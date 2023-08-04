package org.usvm.interpreter.operations.tracing

import mu.KLogging
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.language.SymbolForCPython
import java.util.concurrent.Callable

private val logger = object : KLogging() {}.logger
object PathDiversionException: Exception()

fun <T : Any> withTracing(
    context: ConcolicRunContext,
    newEventParameters: SymbolicHandlerEventParameters<T>,
    resultSupplier: Callable<T?>
): T? {
    if (context.curState == null)
        return null
    context.instructionCounter++
    if (context.instructionCounter > context.curState!!.path.size) {
        val result = runCatching { resultSupplier.call() }.onFailure { System.err.println(it) }.getOrThrow()
        if (context.curState == null)
            return null
        val eventRecord = SymbolicHandlerEvent(newEventParameters, result)
        context.curState!!.path = context.curState!!.path.add(eventRecord)
        return result
    }
    val event = context.curState!!.path[context.instructionCounter - 1]
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
    if (context.curState == null)
        return

    val expectedResult = cond.obj.getToBoolValue(context)?.let {
        context.curState!!.pyModel.eval(it)
    }?.isTrue ?: return

    if (result != expectedResult) {
        logger.debug("Path diversion after fork!")
        logger.debug("Condition: {}", cond.obj.getToBoolValue(context))
        logger.debug("Expected: {}", expectedResult)
        logger.debug("Got: {}", result)
        context.pathDiversion()
    }
}