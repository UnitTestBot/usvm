package org.usvm.machine.interpreters.operations.descriptors

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.MemberDescriptor
import org.usvm.language.SymbolForCPython
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.getSliceStart

object SliceStartDescriptor: MemberDescriptor() {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject): SymbolForCPython? {
        if (ctx.curState == null)
            return null
        val (isNone, content) = owner.getSliceStart(ctx)
        val address = ctx.ctx.mkIte(
            isNone,
            ctx.curState!!.preAllocatedObjects.noneObject.address,
            constructInt(ctx, content).address
        )
        return SymbolForCPython(UninterpretedSymbolicPythonObject(address, ctx.typeSystem), 0)
    }
}