package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.SymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructInt
import org.usvm.language.types.pythonTuple
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerLoadConstLongKt(context: ConcolicRunContext, value: Long): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    return constructInt(context, context.ctx.mkIntNum(value))
}

fun handlerLoadConstTupleKt(context: ConcolicRunContext, elements: Stream<SymbolicPythonObject>): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val addresses = elements.map { it!!.address }.asSequence()
    with (context.ctx) {
        val tupleAddress = context.curState!!.memory.malloc(pythonTuple, addressSort, addresses)
        val result = UninterpretedSymbolicPythonObject(tupleAddress)
        result.addSupertype(context, pythonTuple)
        return result
    }
}