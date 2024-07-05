package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.TupleIteratorContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun UninterpretedSymbolicPythonObject.setTupleIteratorContent(
    ctx: ConcolicRunContext,
    tuple: UninterpretedSymbolicPythonObject,
) = with(
    ctx.ctx
) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    ctx.extractCurState().memory.writeField(address, TupleIteratorContents.tuple, addressSort, tuple.address, trueExpr)
    ctx.extractCurState().memory.writeField(address, TupleIteratorContents.index, intSort, mkIntNum(0), trueExpr)
}

fun UninterpretedSymbolicPythonObject.getTupleIteratorContent(
    ctx: ConcolicRunContext,
): Pair<UHeapRef, UExpr<KIntSort>> = with(
    ctx.ctx
) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    val tupleRef = ctx.extractCurState().memory.readField(address, TupleIteratorContents.tuple, addressSort)
    val index = ctx.extractCurState().memory.readField(address, TupleIteratorContents.index, intSort)
    return tupleRef to index
}

fun UninterpretedSymbolicPythonObject.increaseTupleIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    val oldIndexValue = ctx.extractCurState().memory.readField(address, TupleIteratorContents.index, intSort)
    ctx.extractCurState().memory.writeField(
        address,
        TupleIteratorContents.index,
        intSort,
        mkArithAdd(oldIndexValue, mkIntNum(1)),
        trueExpr
    )
}
