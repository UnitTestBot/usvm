package org.usvm.machine.interpreters.symbolic.operations.symbolicmethods

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerCreateEnumerateKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerFloatCastKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerIntCastKt

fun symbolicMethodIntKt(ctx: ConcolicRunContext, args: Array<SymbolForCPython>): SymbolForCPython? {
    if (args.size != 1) {
        return null
    }
    val value = args[0].obj ?: return null
    return handlerIntCastKt(ctx, value)?.let { SymbolForCPython(it, 0) }
}

fun symbolicMethodFloatKt(ctx: ConcolicRunContext, args: Array<SymbolForCPython>): SymbolForCPython? {
    if (args.size != 1) {
        return null
    }
    val value = args[0].obj ?: return null
    return handlerFloatCastKt(ctx, value)?.let { SymbolForCPython(it, 0) }
}

fun symbolicMethodEnumerateKt(ctx: ConcolicRunContext, args: Array<SymbolForCPython>): SymbolForCPython? {
    if (args.size != 1) {
        return null
    }
    val iterable = args[0].obj ?: return null
    val result = handlerCreateEnumerateKt(ctx, iterable) ?: return null
    return SymbolForCPython(result, 0)
}
