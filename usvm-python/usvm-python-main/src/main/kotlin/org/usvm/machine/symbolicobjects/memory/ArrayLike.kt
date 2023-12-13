package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.writeArrayIndex
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.language.types.ArrayType
import org.usvm.language.types.HasElementConstraint
import org.usvm.machine.PyContext
import org.usvm.machine.interpreters.symbolic.operations.basic.myAssert
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject


fun UninterpretedSymbolicPythonObject.readArrayLength(ctx: ConcolicRunContext): UExpr<KIntSort> {
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val result = ctx.curState!!.memory.readArrayLength(address, ArrayType, ctx.ctx.intSort)
    myAssert(ctx, ctx.ctx.mkArithGe(result, ctx.ctx.mkIntNum(0)))
    return result
}

fun InterpretedInputSymbolicPythonObject.readArrayLength(ctx: PyContext): UExpr<KIntSort> {
    require(getConcreteType() != null && getConcreteType() is ArrayLikeConcretePythonType)
    return modelHolder.model.readArrayLength(address, ArrayType, ctx.intSort)
}

fun UninterpretedSymbolicPythonObject.readArrayElement(ctx: ConcolicRunContext, index: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val elemAddress = ctx.curState!!.memory.readArrayIndex(address, index, ArrayType, ctx.ctx.addressSort)
    val elem = UninterpretedSymbolicPythonObject(elemAddress, typeSystem)
    if (isAllocatedObject(ctx))
        return elem
    val cond = type.elementConstraints.fold(ctx.ctx.trueExpr as UBoolExpr) { acc, constraint ->
        ctx.ctx.mkAnd(acc, constraint.applyUninterpreted(this, elem, ctx))
    }
    myAssert(ctx, cond)
    return elem
}

fun UninterpretedSymbolicPythonObject.writeArrayElement(ctx: ConcolicRunContext, index: UExpr<KIntSort>, value: UninterpretedSymbolicPythonObject) {
    require(ctx.curState != null)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    if (!isAllocatedObject(ctx)) {
        val cond = type.elementConstraints.fold(ctx.ctx.trueExpr as UBoolExpr) { acc, constraint ->
            ctx.ctx.mkAnd(acc, constraint.applyUninterpreted(this, value, ctx))
        }
        myAssert(ctx, cond)
    }
    ctx.curState!!.memory.writeArrayIndex(address, index, ArrayType, ctx.ctx.addressSort, value.address, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.extendArrayConstraints(ctx: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    require(ctx.curState != null)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    type.elementConstraints.forEach {  constraint ->
        on.addSupertypeSoft(ctx, HasElementConstraint(constraint))
    }
}
