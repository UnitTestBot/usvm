package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.EnumerateContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun UninterpretedSymbolicPythonObject.initializeEnumerate(
    ctx: ConcolicRunContext,
    arg: UninterpretedSymbolicPythonObject,
) = with(ctx.ctx) {
    require(ctx.curState != null)
    ctx.curState!!.memory.writeField(address, EnumerateContents.iterator, addressSort, arg.address, trueExpr)
    ctx.curState!!.memory.writeField(address, EnumerateContents.index, intSort, mkIntNum(0), trueExpr)
}

fun UninterpretedSymbolicPythonObject.getEnumerateIterator(
    ctx: ConcolicRunContext,
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    val result = ctx.curState!!.memory.readField(address, EnumerateContents.iterator, ctx.ctx.addressSort)
    return UninterpretedSymbolicPythonObject(result, typeSystem)
}

fun UninterpretedSymbolicPythonObject.getEnumerateIndexAndIncrement(
    ctx: ConcolicRunContext,
): UExpr<KIntSort> = with(ctx.ctx) {
    require(ctx.curState != null)
    val result = ctx.curState!!.memory.readField(address, EnumerateContents.index, intSort)
    ctx.curState!!.memory.writeField(
        address,
        EnumerateContents.index,
        intSort,
        mkArithAdd(result, mkIntNum(1)),
        trueExpr
    )
    return result
}
