package org.usvm.machine.interpreters.symbolic.operations.symbolicmethods

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerListAppendKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerListClearKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerListExtendKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerListInsertKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerListPopIndKt
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerListPopKt

fun symbolicMethodListAppendKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    if (self?.obj == null || args.size != 1 || args.first().obj == null) {
        return null
    }
    val result = handlerListAppendKt(ctx, self.obj!!, args.first().obj!!)
    return SymbolForCPython(result, 0)
}

fun symbolicMethodListPopKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    if (self?.obj == null || args.size > 1) {
        return null
    }
    val result = if (args.isEmpty()) {
        handlerListPopKt(ctx, self.obj!!)
    } else {
        if (args.first().obj == null) {
            return null
        }
        handlerListPopIndKt(ctx, self.obj!!, args.first().obj!!)
    }
    return SymbolForCPython(result, 0)
}

fun symbolicMethodListInsertKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    if (self?.obj == null || args.size != 2 || args[0].obj == null || args[1].obj == null) {
        return null
    }
    handlerListInsertKt(ctx, self.obj!!, args[0].obj!!, args[1].obj!!)
    return generateNone(ctx)
}

fun symbolicMethodListExtendKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    if (self?.obj == null || args.size != 1 || args.first().obj == null) {
        return null
    }
    val result = handlerListExtendKt(ctx, self.obj!!, args.first().obj!!)
    return SymbolForCPython(result, 0)
}

fun symbolicMethodListClearKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    if (self?.obj == null || args.isNotEmpty()) {
        return null
    }
    handlerListClearKt(ctx, self.obj!!)
    return generateNone(ctx)
}
