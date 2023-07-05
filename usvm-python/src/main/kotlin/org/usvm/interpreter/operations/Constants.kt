package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.SymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructObject
import org.usvm.language.pythonInt

fun handlerLoadConstLongKt(context: ConcolicRunContext, value: Long): UninterpretedSymbolicPythonObject {
    return constructObject(context.ctx.mkIntNum(value), pythonInt, context.ctx, context.curState.memory)
}