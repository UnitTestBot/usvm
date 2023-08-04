package org.usvm.interpreter.operations

import org.usvm.forkMulti
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.PythonExecutionState
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructBool
import org.usvm.interpreter.symbolicobjects.interpretSymbolicPythonObject
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

        var negationState: PythonExecutionState? = ctx.curState!!.clone()  // state with concrete type
        negationState = forkMulti(
            negationState!!,
            listOf(negationState.pathConstraints.typeConstraints.evalIsSubtype(obj.address, type))
        ).single()
        if (negationState != null)
            ctx.forkedStates.add(negationState)

        myAssert(ctx, obj.evalIs(ctx, type).not())  // our state must contain type stream with excluded type
        require(interpreted.getConcreteType(ctx) == null)
        constructBool(ctx, ctx.ctx.falseExpr)
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