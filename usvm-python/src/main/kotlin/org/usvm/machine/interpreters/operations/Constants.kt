package org.usvm.machine.interpreters.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructInt
import java.util.stream.Stream
import kotlin.streams.asSequence

fun handlerLoadConstKt(context: ConcolicRunContext, value: PythonObject): UninterpretedSymbolicPythonObject? =
    when (ConcretePythonInterpreter.getPythonObjectTypeName(value)) {
        "int" -> handlerLoadConstLongKt(context, ConcretePythonInterpreter.getPythonObjectRepr(value))
        "bool" -> handlerLoadConstBoolKt(context, ConcretePythonInterpreter.getPythonObjectRepr(value))
        "NoneType" -> context.curState?.preAllocatedObjects?.noneObject
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

fun handlerLoadConstBoolKt(context: ConcolicRunContext, value: String): UninterpretedSymbolicPythonObject? {
    if (context.curState == null)
        return null
    return when (value) {
        "True" -> constructBool(context, context.ctx.trueExpr)
        "False" -> constructBool(context, context.ctx.falseExpr)
        else -> error("Not reachable")
    }
}

fun handlerLoadConstTupleKt(context: ConcolicRunContext, elements: List<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(context, elements, context.typeSystem.pythonTuple)