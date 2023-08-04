package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructBool
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.language.types.ConcreteTypeNegation
import org.usvm.language.types.PythonTypeSystem
import org.usvm.language.types.pythonBool
import org.usvm.language.types.pythonObjectType

fun handlerIsinstanceKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject, typeRef: PythonObject): UninterpretedSymbolicPythonObject? = with(ctx.ctx) {
    ctx.curState ?: return null
    val type = PythonTypeSystem.getConcreteTypeByAddress(typeRef) ?: return null
    if (type == pythonObjectType)
        return constructBool(ctx, ctx.ctx.trueExpr)

    val interpreted = interpretSymbolicPythonObject(obj, ctx.modelHolder)
    return if (interpreted.getConcreteType(ctx) == null) {
        myFork(ctx, obj.rawEvalIs(ctx, ConcreteTypeNegation(type)).not())
        require(interpreted.getConcreteType(ctx) == null)
        constructBool(ctx, falseExpr)
    } else {
        constructBool(ctx, obj.evalIs(ctx, type))
    }
}

fun addConcreteSupertypeKt(ctx: ConcolicRunContext, obj: UninterpretedSymbolicPythonObject, typeRef: PythonObject) {
    ctx.curState ?: return
    val type = PythonTypeSystem.getConcreteTypeByAddress(typeRef) ?: return
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