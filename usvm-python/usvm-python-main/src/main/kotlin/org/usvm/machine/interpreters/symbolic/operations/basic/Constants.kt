package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructFloat
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedFloatWithValue

fun handlerLoadConstKt(context: ConcolicRunContext, value: PyObject): UninterpretedSymbolicPythonObject? {
    if (ConcretePythonInterpreter.pythonExceptionOccurred()) {
        return null
    }
    return when (ConcretePythonInterpreter.getPythonObjectTypeName(value)) {
        "int" -> handlerLoadConstLongKt(context, value)
        "bool" -> handlerLoadConstBoolKt(context, ConcretePythonInterpreter.getPythonObjectRepr(value))
        "NoneType" -> context.curState?.preAllocatedObjects?.noneObject
        "tuple" -> {
            val elements = ConcretePythonInterpreter.getIterableElements(value)
            val symbolicElements = elements.map {
                handlerLoadConstKt(context, it) ?: return null
            }
            handlerLoadConstTupleKt(context, symbolicElements)
        }

        "str" -> handlerLoadConstStrKt(context, value)
        "float" -> handlerLoadConstFloatKt(context, value)
        else -> null
    }
}

fun handlerLoadConstStrKt(context: ConcolicRunContext, value: PyObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null) {
        return null
    }
    val str = ConcretePythonInterpreter.getPythonObjectStr(value)
    return context.curState!!.preAllocatedObjects.allocateStr(context, str, value)
}

fun handlerLoadConstLongKt(context: ConcolicRunContext, value: PyObject): UninterpretedSymbolicPythonObject? {
    if (context.curState == null) {
        return null
    }
    val str = runCatching {
        ConcretePythonInterpreter.getPythonObjectRepr(value)
    }.onFailure {
        System.err.println("Failed to get repr of int at ${value.address}")
        val attempt2 = ConcretePythonInterpreter.getPythonObjectRepr(value, printErrorMsg = true)
        System.err.println("Attempt 2: $attempt2")
    }.getOrThrow()

    return constructInt(context, context.ctx.mkIntNum(str))
}

fun handlerLoadConstFloatKt(ctx: ConcolicRunContext, value: PyObject): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val str = ConcretePythonInterpreter.getPythonObjectRepr(value)
    val doubleValue = str.toDoubleOrNull() ?: return null
    return constructFloat(ctx, mkUninterpretedFloatWithValue(ctx.ctx, doubleValue))
}

fun handlerLoadConstBoolKt(context: ConcolicRunContext, value: String): UninterpretedSymbolicPythonObject? {
    if (context.curState == null) {
        return null
    }
    return when (value) {
        "True" -> constructBool(context, context.ctx.trueExpr)
        "False" -> constructBool(context, context.ctx.falseExpr)
        else -> error("Not reachable")
    }
}

fun handlerLoadConstTupleKt(context: ConcolicRunContext, elements: List<UninterpretedSymbolicPythonObject>): UninterpretedSymbolicPythonObject? =
    createIterable(context, elements, context.typeSystem.pythonTuple)
