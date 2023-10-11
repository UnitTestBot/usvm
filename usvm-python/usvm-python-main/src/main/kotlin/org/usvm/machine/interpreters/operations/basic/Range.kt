package org.usvm.machine.interpreters.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.machine.symbolicobjects.*

fun handlerCreateRangeKt(
    ctx: ConcolicRunContext,
    start: UninterpretedSymbolicPythonObject,
    stop: UninterpretedSymbolicPythonObject,
    step: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    myFork(ctx, ctx.ctx.mkEq(step.getIntContent(ctx), ctx.ctx.mkIntNum(0)))
    return constructRange(ctx, start.getIntContent(ctx), stop.getIntContent(ctx), step.getIntContent(ctx))
}

fun handlerRangeIterKt(
    ctx: ConcolicRunContext,
    range: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    return constructRangeIterator(ctx, range)
}

fun handlerRangeIteratorNextKt(
    ctx: ConcolicRunContext,
    rangeIterator: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null)
        return null
    val (index, length) = rangeIterator.getRangeIteratorState(ctx)
    myFork(ctx, index lt length)
    if (ctx.modelHolder.model.eval(index lt length).isTrue) {
        val value = rangeIterator.getRangeIteratorNext(ctx)
        return constructInt(ctx, value)
    }
    return null
}