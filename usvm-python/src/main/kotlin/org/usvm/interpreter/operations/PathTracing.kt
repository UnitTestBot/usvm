package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.SymbolicHandlerEvent
import org.usvm.interpreter.SymbolicHandlerEventParameters

fun <T : Any> withTracing(
    context: ConcolicRunContext,
    newEventParameters: SymbolicHandlerEventParameters<T>,
    resultSupplier: () -> T?
): T? {
    context.instructionCounter++
    if (context.instructionCounter > context.curState.path.size) {
        val result = resultSupplier()
        val eventRecord = SymbolicHandlerEvent(newEventParameters, result)
        context.curState.path = context.curState.path.add(eventRecord)
        return result
    }
    val event = context.curState.path[context.instructionCounter - 1]
    if (event.parameters != newEventParameters) {
        println("Path diversion!")
        println(event.parameters)
        println(newEventParameters)
        System.out.flush()
        throw PathDiversionException
    }
    event.result ?: return null

    @Suppress("unchecked_cast")
    return event.result as T
}

object PathDiversionException: Exception()