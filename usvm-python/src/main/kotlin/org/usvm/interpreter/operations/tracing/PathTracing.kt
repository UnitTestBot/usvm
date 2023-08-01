package org.usvm.interpreter.operations.tracing

import mu.KLogging
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
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
    }
    event.result ?: return null

    @Suppress("unchecked_cast")
    return event.result as T
}


// TODO: there might be events between fork and fork result
fun handlerForkResultKt(context: ConcolicRunContext, result: Boolean) {
    if (context.instructionCounter < 1 || context.curState == null)
        return
    val lastEventParams = context.curState!!.path[context.instructionCounter - 1].parameters
    if (lastEventParams !is Fork)
        return

    val expectedResult = lastEventParams.condition.obj.getToBoolValue(context)?.let {
        context.curState!!.pyModel.eval(it)
    }?.isTrue ?: return

    if (result != expectedResult)
        context.pathDiversion()
}