package org.usvm.machine.interpreters.symbolic.operations.nativecalls

import org.usvm.annotations.ids.NativeId
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isFalse
import org.usvm.machine.interpreters.symbolic.operations.basic.myAssert
import org.usvm.machine.interpreters.symbolic.operations.basic.myFork
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

object EvalConstraint : NativeCallConstraint(NativeId.Eval) {
    override fun apply(ctx: ConcolicRunContext, args: List<UninterpretedSymbolicPythonObject>) {
        if (args.size > 3 || args.isEmpty()) {
            return
        }
        val cmd = args[0]
        cmd.addSupertype(ctx, ctx.typeSystem.pythonStr)
        args.drop(1).forEach {
            val isDictCond = it.evalIs(ctx, ctx.typeSystem.pythonDict)
            if (ctx.modelHolder.model.eval(isDictCond).isFalse) {
                myFork(ctx, isDictCond)
                it.addSupertype(ctx, ctx.typeSystem.pythonNoneType)
            } else {
                myAssert(ctx, isDictCond)
            }
        }
    }
}
