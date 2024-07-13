package org.usvm.machine.interpreters.symbolic.operations.symbolicmethods

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
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
    val selfObg = self?.obj
    val argObj = args.firstOrNull()?.obj
    if (selfObg == null || args.size != 1 || argObj == null) {
        return null
    }
    val result = handlerListAppendKt(ctx, selfObg, argObj)
    return SymbolForCPython(result, 0)
}

fun symbolicMethodListPopKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    val selfObj = self?.obj
    if (selfObj == null || args.size > 1) {
        return null
    }
    val result = if (args.isEmpty()) {
        handlerListPopKt(ctx, selfObj)
    } else {
        val argObj = args.first().obj ?: return null
        handlerListPopIndKt(ctx, selfObj, argObj)
    }
    return SymbolForCPython(result, 0)
}

fun symbolicMethodListInsertKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    val selfObj = self?.obj
    val arg0Obj = args.getOrNull(0)?.obj
    val arg1Obj = args.getOrNull(1)?.obj
    if (selfObj == null || args.size != 2 || arg0Obj == null || arg1Obj == null) {
        return null
    }
    handlerListInsertKt(ctx, selfObj, arg0Obj, arg1Obj)
    return generateNone(ctx)
}

fun symbolicMethodListExtendKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    val selfObj = self?.obj
    val argObj = args.getOrNull(0)?.obj
    if (selfObj == null || args.size != 1 || argObj == null) {
        return null
    }
    val result = handlerListExtendKt(ctx, selfObj, argObj)
    return SymbolForCPython(result, 0)
}

fun symbolicMethodListClearKt(
    ctx: ConcolicRunContext,
    self: SymbolForCPython?,
    args: Array<SymbolForCPython>,
): SymbolForCPython? {
    val selfObj = self?.obj
    if (selfObj == null || args.isNotEmpty()) {
        return null
    }
    handlerListClearKt(ctx, selfObj)
    return generateNone(ctx)
}
