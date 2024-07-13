package org.usvm.machine.interpreters.symbolic.operations.descriptors

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.memory.SliceUninterpretedField
import org.usvm.machine.symbolicobjects.memory.getSliceStart
import org.usvm.machine.symbolicobjects.memory.getSliceStep
import org.usvm.machine.symbolicobjects.memory.getSliceStop

private fun constructResult(field: SliceUninterpretedField, ctx: ConcolicRunContext): SymbolForCPython {
    val (isNone, content) = field
    val address = ctx.ctx.mkIte(
        isNone,
        ctx.extractCurState().preAllocatedObjects.noneObject.address,
        constructInt(ctx, content).address
    )
    return SymbolForCPython(
        UninterpretedSymbolicPythonObject(address, ctx.typeSystem),
        0
    )
}

object SliceStartDescriptor : MemberDescriptor {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython? {
        if (ctx.curState == null) {
            return null
        }
        owner ?: return null
        return constructResult(owner.getSliceStart(ctx), ctx)
    }
}

object SliceStopDescriptor : MemberDescriptor {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython? {
        if (ctx.curState == null) {
            return null
        }
        owner ?: return null
        return constructResult(owner.getSliceStop(ctx), ctx)
    }
}

object SliceStepDescriptor : MemberDescriptor {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython? {
        if (ctx.curState == null) {
            return null
        }
        owner ?: return null
        return constructResult(owner.getSliceStep(ctx), ctx)
    }
}
