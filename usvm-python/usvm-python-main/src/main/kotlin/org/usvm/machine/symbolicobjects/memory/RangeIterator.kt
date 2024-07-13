package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.RangeContents
import org.usvm.machine.symbolicobjects.RangeIteratorContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun UninterpretedSymbolicPythonObject.setRangeIteratorContent(
    ctx: ConcolicRunContext,
    range: UninterpretedSymbolicPythonObject,
) = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, ctx.typeSystem.pythonRangeIterator)
    val start = ctx.extractCurState().memory.readField(range.address, RangeContents.start, intSort)
    ctx.extractCurState().memory.writeField(address, RangeIteratorContents.start, intSort, start, trueExpr)
    val length = ctx.extractCurState().memory.readField(range.address, RangeContents.length, intSort)
    ctx.extractCurState().memory.writeField(address, RangeIteratorContents.length, intSort, length, trueExpr)
    val step = ctx.extractCurState().memory.readField(range.address, RangeContents.step, intSort)
    ctx.extractCurState().memory.writeField(address, RangeIteratorContents.step, intSort, step, trueExpr)
    val index = mkIntNum(0)
    ctx.extractCurState().memory.writeField(address, RangeIteratorContents.index, intSort, index, trueExpr)
}

fun UninterpretedSymbolicPythonObject.getRangeIteratorState(
    ctx: ConcolicRunContext,
): Pair<UExpr<KIntSort>, UExpr<KIntSort>> = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
    val index = ctx.extractCurState().memory.readField(address, RangeIteratorContents.index, intSort)
    val length = ctx.extractCurState().memory.readField(address, RangeIteratorContents.length, intSort)
    return index to length
}

fun UninterpretedSymbolicPythonObject.getRangeIteratorNext(
    ctx: ConcolicRunContext,
): UExpr<KIntSort> = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
    val index = ctx.extractCurState().memory.readField(address, RangeIteratorContents.index, intSort)
    val newIndex = mkArithAdd(index, mkIntNum(1))
    ctx.extractCurState().memory.writeField(address, RangeIteratorContents.index, intSort, newIndex, trueExpr)
    val start = ctx.extractCurState().memory.readField(address, RangeIteratorContents.start, intSort)
    val step = ctx.extractCurState().memory.readField(address, RangeIteratorContents.step, intSort)
    return mkArithAdd(start, mkArithMul(index, step))
}
