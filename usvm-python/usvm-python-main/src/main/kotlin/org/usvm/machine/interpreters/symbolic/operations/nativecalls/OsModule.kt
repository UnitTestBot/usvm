package org.usvm.machine.interpreters.symbolic.operations.nativecalls

import org.usvm.annotations.ids.NativeId
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

object OsSystemConstraint: NativeCallConstraint(NativeId.OsSystem) {
    override fun apply(ctx: ConcolicRunContext, args: List<UninterpretedSymbolicPythonObject>) {
        if (args.size != 1)
            return
        val arg = args[0]
        arg.addSupertype(ctx, ctx.typeSystem.pythonStr)
    }
}