package org.usvm.machine.interpreters.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.symbolicobjects.SymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.language.types.pythonTuple

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