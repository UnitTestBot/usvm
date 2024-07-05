package org.usvm.machine.interpreters.symbolic.operations.nativecalls

import org.usvm.annotations.ids.NativeId
import org.usvm.isFalse
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.symbolic.operations.basic.pyAssert
import org.usvm.machine.interpreters.symbolic.operations.basic.pyFork
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

/**
 * This file is supposed to contain implementations of [NativeCallConstraint]
 *
 * All implementations must be listed in [constraintHolder] to be registered.
 * */
val constraintHolder = listOf(
    OsSystemConstraint,
    EvalConstraint
)

object EvalConstraint : NativeCallConstraint(NativeId.Eval) {
    private const val MAX_ARGS = 3
    override fun apply(ctx: ConcolicRunContext, args: List<UninterpretedSymbolicPythonObject>) {
        if (args.size > MAX_ARGS || args.isEmpty()) {
            return
        }
        val cmd = args[0]
        cmd.addSupertype(ctx, ctx.typeSystem.pythonStr)
        args.drop(1).forEach {
            val isDictCond = it.evalIs(ctx, ctx.typeSystem.pythonDict)
            if (ctx.modelHolder.model.eval(isDictCond).isFalse) {
                pyFork(ctx, isDictCond)
                it.addSupertype(ctx, ctx.typeSystem.pythonNoneType)
            } else {
                pyAssert(ctx, isDictCond)
            }
        }
    }
}

object OsSystemConstraint : NativeCallConstraint(NativeId.OsSystem) {
    override fun apply(ctx: ConcolicRunContext, args: List<UninterpretedSymbolicPythonObject>) {
        if (args.size != 1) {
            return
        }
        val arg = args[0]
        arg.addSupertype(ctx, ctx.typeSystem.pythonStr)
    }
}
