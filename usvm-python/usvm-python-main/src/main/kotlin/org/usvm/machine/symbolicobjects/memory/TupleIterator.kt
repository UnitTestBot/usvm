package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.TupleIteratorContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun UninterpretedSymbolicPythonObject.setTupleIteratorContent(ctx: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject) = with(
    ctx.ctx
) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    ctx.curState!!.memory.writeField(address, TupleIteratorContents.tuple, addressSort, tuple.address, trueExpr)
    ctx.curState!!.memory.writeField(address, TupleIteratorContents.index, intSort, mkIntNum(0), trueExpr)
}

fun UninterpretedSymbolicPythonObject.getTupleIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(
    ctx.ctx
) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    val tupleRef = ctx.curState!!.memory.readField(address, TupleIteratorContents.tuple, addressSort)
    val index = ctx.curState!!.memory.readField(address, TupleIteratorContents.index, intSort)
    return tupleRef to index
}

fun UninterpretedSymbolicPythonObject.increaseTupleIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    val oldIndexValue = ctx.curState!!.memory.readField(address, TupleIteratorContents.index, intSort)
    ctx.curState!!.memory.writeField(
        address,
        TupleIteratorContents.index,
        intSort,
        mkArithAdd(oldIndexValue, mkIntNum(1)),
        trueExpr
    )
}
