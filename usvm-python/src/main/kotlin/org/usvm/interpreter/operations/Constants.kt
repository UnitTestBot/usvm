package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.symbolicobjects.SymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructInt
import org.usvm.language.types.pythonTuple
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerLoadConstKt(context: ConcolicRunContext, value: PythonObject): UninterpretedSymbolicPythonObject? =
    when (ConcretePythonInterpreter.getPythonObjectTypeName(value)) {
        "int" -> handlerLoadConstLongKt(context, ConcretePythonInterpreter.getPythonObjectRepr(value))
        "tuple" -> {
            val elements = ConcretePythonInterpreter.getIterableElements(value)
            val symbolicElements = elements.map {
                handlerLoadConstKt(context, it) ?: return null
            }
            handlerLoadConstTupleKt(context, symbolicElements)
        }
        else -> null
    }

fun handlerLoadConstLongKt(context: ConcolicRunContext, value: String): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    return constructInt(context, context.ctx.mkIntNum(value))
}

fun handlerLoadConstTupleKt(context: ConcolicRunContext, elements: List<SymbolicPythonObject>): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    val addresses = elements.map { it.address }.asSequence()
    with (context.ctx) {
        val tupleAddress = context.curState!!.memory.malloc(pythonTuple, addressSort, addresses)
        val result = UninterpretedSymbolicPythonObject(tupleAddress)
        result.addSupertype(context, pythonTuple)
        return result
    }
}