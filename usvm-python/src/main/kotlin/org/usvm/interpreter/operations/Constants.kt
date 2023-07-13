package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructInt

fun handlerLoadConstLongKt(context: ConcolicRunContext, value: Long): UninterpretedSymbolicPythonObject =
    constructInt(context, context.ctx.mkIntNum(value))