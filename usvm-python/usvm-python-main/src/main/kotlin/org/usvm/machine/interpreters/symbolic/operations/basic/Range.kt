package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.constructRange
import org.usvm.machine.symbolicobjects.constructRangeIterator
import org.usvm.machine.symbolicobjects.memory.getIntContent
import org.usvm.machine.symbolicobjects.memory.getRangeIteratorNext
import org.usvm.machine.symbolicobjects.memory.getRangeIteratorState

fun handlerCreateRangeKt(
    ctx: ConcolicRunContext,
    start: UninterpretedSymbolicPythonObject,
    stop: UninterpretedSymbolicPythonObject,
    step: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    pyFork(ctx, ctx.ctx.mkEq(step.getIntContent(ctx), ctx.ctx.mkIntNum(0)))
    return constructRange(ctx, start.getIntContent(ctx), stop.getIntContent(ctx), step.getIntContent(ctx))
}

fun handlerRangeIterKt(
    ctx: ConcolicRunContext,
    range: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    return constructRangeIterator(ctx, range)
}

fun handlerRangeIteratorNextKt(
    ctx: ConcolicRunContext,
    rangeIterator: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    if (ctx.curState == null) {
        return null
    }
    val (index, length) = rangeIterator.getRangeIteratorState(ctx)
    pyFork(ctx, index lt length)
    if (ctx.modelHolder.model.eval(index lt length).isTrue) {
        val value = rangeIterator.getRangeIteratorNext(ctx)
        return constructInt(ctx, value)
    }
    return null
}
