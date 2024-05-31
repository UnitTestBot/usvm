package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.ListIteratorContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject


fun UninterpretedSymbolicPythonObject.setListIteratorContent(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
) = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, typeSystem.pythonListIteratorType)
    ctx.curState!!.memory.writeField(address, ListIteratorContents.list, addressSort, list.address, trueExpr)
    ctx.curState!!.memory.writeField(address, ListIteratorContents.index, intSort, mkIntNum(0), trueExpr)
}

fun UninterpretedSymbolicPythonObject.increaseListIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, typeSystem.pythonListIteratorType)
    val oldIndexValue = ctx.curState!!.memory.readField(address, ListIteratorContents.index, intSort)
    ctx.curState!!.memory.writeField(
        address,
        ListIteratorContents.index,
        intSort,
        mkArithAdd(oldIndexValue, mkIntNum(1)),
        trueExpr
    )
}

fun UninterpretedSymbolicPythonObject.getListIteratorContent(
    ctx: ConcolicRunContext,
): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    addSupertype(ctx, typeSystem.pythonListIteratorType)
    val listRef = ctx.curState!!.memory.readField(address, ListIteratorContents.list, addressSort)
    val index = ctx.curState!!.memory.readField(address, ListIteratorContents.index, intSort)
    return listRef to index
}
