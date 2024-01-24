package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PyCallable
import org.usvm.language.types.PythonType
import org.usvm.machine.PyContext
import org.usvm.machine.symbolicobjects.*
import org.usvm.memory.UMemory


fun UninterpretedSymbolicPythonObject.setIntContent(ctx: ConcolicRunContext, expr: UExpr<KIntSort>) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonInt)
    ctx.curState!!.memory.writeField(address, IntContents.content, ctx.ctx.intSort, expr, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
    require(ctx.curState != null)
    addSupertype(ctx, typeSystem.pythonInt)
    return ctx.curState!!.memory.readField(address, IntContents.content, ctx.ctx.intSort)
}

fun UninterpretedSymbolicPythonObject.getToIntContent(ctx: ConcolicRunContext): UExpr<KIntSort>? = with(ctx.ctx) {
    return when (getTypeIfDefined(ctx)) {
        typeSystem.pythonInt -> getIntContent(ctx)
        typeSystem.pythonBool -> mkIte(getBoolContent(ctx), mkIntNum(1), mkIntNum(0))
        else -> null
    }
}

fun InterpretedInputSymbolicPythonObject.getIntContent(ctx: PyContext): UExpr<KIntSort> {
    require(getConcreteType() == typeSystem.pythonInt)
    return modelHolder.model.readField(address, IntContents.content, ctx.intSort)
}

fun InterpretedSymbolicPythonObject.getIntContent(ctx: PyContext, memory: UMemory<PythonType, PyCallable>): UExpr<KIntSort> {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getIntContent(ctx)
        }
        is InterpretedAllocatedOrStaticSymbolicPythonObject -> {
            memory.readField(address, IntContents.content, ctx.intSort)
        }
    }
}

fun InterpretedSymbolicPythonObject.getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
    require(ctx.curState != null)
    return getIntContent(ctx.ctx, ctx.curState!!.memory)
}
