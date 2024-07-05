package org.usvm.machine.interpreters.symbolic.operations.symbolicmethods

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerLoadConstKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerSetAddKt

fun symbolicMethodSetAddKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    val selfObj = self?.obj
    val argObj = args.firstOrNull()?.obj
    if (selfObj == null || ctx.curState == null || args.size != 1 || argObj == null) {
        return null
    }
    handlerSetAddKt(ctx, selfObj, argObj)
    val none = PyObject(ConcretePythonInterpreter.pyNoneRef)
    return SymbolForCPython(handlerLoadConstKt(ctx, none), 0)
}
