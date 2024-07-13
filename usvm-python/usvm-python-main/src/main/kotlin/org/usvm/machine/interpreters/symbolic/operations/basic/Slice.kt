package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructSlice
import org.usvm.machine.symbolicobjects.memory.SliceUninterpretedField
import org.usvm.machine.symbolicobjects.memory.getIntContent

private fun getFieldContent(
    ctx: ConcolicRunContext,
    value: UninterpretedSymbolicPythonObject,
): SliceUninterpretedField {
    val typeSystem = ctx.typeSystem
    val isNone = value.evalIs(ctx, typeSystem.pythonNoneType)
    val content =
        if (value.getTypeIfDefined(ctx) == typeSystem.pythonInt) {
            value.getIntContent(ctx)
        } else {
            ctx.ctx.mkIntNum(0)
        }
    pyFork(ctx, value.evalIs(ctx, typeSystem.pythonInt))
    pyAssert(ctx, ctx.ctx.mkOr(value.evalIs(ctx, typeSystem.pythonInt), value.evalIs(ctx, typeSystem.pythonNoneType)))
    return SliceUninterpretedField(isNone, content)
}

fun handlerCreateSliceKt(
    ctx: ConcolicRunContext,
    start: UninterpretedSymbolicPythonObject,
    stop: UninterpretedSymbolicPythonObject,
    step: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val startContent = getFieldContent(ctx, start)
    val stopContent = getFieldContent(ctx, stop)
    val stepContent = getFieldContent(ctx, step)
    return constructSlice(ctx, startContent, stopContent, stepContent)
}
