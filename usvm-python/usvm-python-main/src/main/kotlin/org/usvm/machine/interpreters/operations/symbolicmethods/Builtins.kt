package org.usvm.machine.interpreters.operations.symbolicmethods

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.operations.basic.handlerFloatCastKt
import org.usvm.machine.interpreters.operations.basic.handlerIntCastKt

fun symbolicMethodIntKt(ctx: ConcolicRunContext, args: Array<SymbolForCPython>): SymbolForCPython? {
    if (args.size != 1)
        return null
    val value = args[0].obj ?: return null
    return handlerIntCastKt(ctx, value)?.let { SymbolForCPython(it, 0) }
}

fun symbolicMethodFloatKt(ctx: ConcolicRunContext, args: Array<SymbolForCPython>): SymbolForCPython? {
    if (args.size != 1)
        return null
    val value = args[0].obj ?: return null
    return handlerFloatCastKt(ctx, value)?.let { SymbolForCPython(it, 0) }
}