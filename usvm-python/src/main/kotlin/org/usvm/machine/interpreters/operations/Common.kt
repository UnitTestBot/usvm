package org.usvm.machine.interpreters.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.language.types.ConcreteTypeNegation
import org.usvm.language.types.pythonBool
import org.usvm.language.types.pythonObjectType

fun handlerIsinstanceKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject, typeRef: PythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    val type = ctx.typeSystem.getConcreteTypeByAddress(typeRef) ?: return null
    if (type == pythonObjectType)
        return constructBool(ctx, ctx.ctx.trueExpr)

    val interpreted = interpretSymbolicPythonObject(obj, ctx.modelHolder)
    return if (interpreted.getConcreteType(ctx) == null) {
        myFork(ctx, obj.evalIs(ctx, ConcreteTypeNegation(type)))
        require(interpreted.getConcreteType(ctx) == null)
        constructBool(ctx, falseExpr)
    } else {
        constructBool(ctx, obj.evalIs(ctx, type))
    }
}

fun addConcreteSupertypeKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject, typeRef: PythonObject) {
    ctx.curState ?: return
    val type = ctx.typeSystem.getConcreteTypeByAddress(typeRef) ?: return
    obj.addSupertype(ctx, type)
}

fun handlerAndKt(ctx: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    left.addSupertype(ctx, pythonBool)
    right.addSupertype(ctx, pythonBool)
    val leftValue = left.getBoolContent(ctx)
    val rightValue = right.getBoolContent(ctx)
    return constructBool(ctx, mkAnd(leftValue, rightValue))
}