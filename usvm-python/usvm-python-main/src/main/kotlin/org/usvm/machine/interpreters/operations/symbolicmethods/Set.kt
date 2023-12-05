package org.usvm.machine.interpreters.operations.symbolicmethods

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.operations.basic.handlerLoadConstKt
import org.usvm.machine.interpreters.operations.basic.handlerSetAddKt

fun symbolicMethodSetAddKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>
): SymbolForCPython? {
    if (self?.obj == null || ctx.curState == null || args.size != 1 || args[0].obj == null)
        return null
    handlerSetAddKt(ctx, self.obj!!, args[0].obj!!)
    val none = PythonObject(ConcretePythonInterpreter.pyNoneRef)
    return SymbolForCPython(handlerLoadConstKt(ctx, none), 0)
}