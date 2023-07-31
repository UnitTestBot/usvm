package org.usvm.interpreter.operations.tracing

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import java.util.concurrent.Callable

fun <T : Any> withTracing(
    context: ConcolicRunContext,
    newEventParameters: SymbolicHandlerEventParameters<T>,
    resultSupplier: Callable<T?>
): T? {
    context.instructionCounter++
    if (context.instructionCounter > context.curState.path.size) {
        val result = runCatching { resultSupplier.call() }.onFailure { System.err.println(it) }.getOrThrow()
        val eventRecord = SymbolicHandlerEvent(newEventParameters, result)
        context.curState.path = context.curState.path.add(eventRecord)
        return result
    }
    val event = context.curState.path[context.instructionCounter - 1]
    if (event.parameters != newEventParameters) {
        println("Path diversion!")
        println("Expected: ${event.parameters}")
        println("Got: $newEventParameters")
        System.out.flush()
        throw PathDiversionException
    }
    event.result ?: return null

    @Suppress("unchecked_cast")
    return event.result as T
}

object PathDiversionException: Exception()


// TODO: there might be events between fork and fork result
fun handlerForkResultKt(context: ConcolicRunContext, result: Boolean) {
    if (context.instructionCounter < 1)
        return
    val lastEventParams = context.curState.path[context.instructionCounter - 1].parameters
    if (lastEventParams !is Fork)
        return

    val expectedResult = lastEventParams.condition.obj.getToBoolValue(context)?.let {
        context.curState.pyModel.eval(it)
    }?.isTrue ?: return

    if (result != expectedResult)
        throw PathDiversionException
}