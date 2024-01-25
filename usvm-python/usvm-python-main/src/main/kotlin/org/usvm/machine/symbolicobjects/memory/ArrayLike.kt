package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.language.types.ArrayType
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.HasElementConstraint
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.interpreters.symbolic.operations.basic.myAssert
import org.usvm.machine.symbolicobjects.*
import org.usvm.types.first


const val DEFAULT_ELEMENT_INDEX = -1

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

fun UninterpretedSymbolicPythonObject.defaultElement(ctx: ConcolicRunContext): UHeapRef {
    val type = getTypeIfDefined(ctx)
    require(ctx.curState != null && type != null && type is ArrayLikeConcretePythonType)
    val result = ctx.curState!!.memory.readArrayIndex(address, ctx.ctx.mkIntNum(DEFAULT_ELEMENT_INDEX), ArrayType, ctx.ctx.addressSort)
    val obj = UninterpretedSymbolicPythonObject(result, ctx.typeSystem)
    val array = this
    val cond = with(ctx.ctx) {
        type.elementConstraints.fold(trueExpr as UBoolExpr) { acc, constraint ->
            acc and constraint.applyUninterpreted(array, obj, ctx)
        }
    }
    myAssert(ctx, cond)
    return result
}

fun InterpretedInputSymbolicPythonObject.defaultElement(ctx: PyContext): UConcreteHeapRef =
    modelHolder.model.readArrayIndex(address, ctx.mkIntNum(DEFAULT_ELEMENT_INDEX), ArrayType, ctx.addressSort) as UConcreteHeapRef

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
    val actualAddress = with(ctx.ctx) { mkIte(mkHeapRefEq(elemAddress, nullRef), defaultElement(ctx), elemAddress) }
    return UninterpretedSymbolicPythonObject(actualAddress, typeSystem)
}

fun InterpretedInputSymbolicPythonObject.readArrayElement(
    indexExpr: KInterpretedValue<KIntSort>,
    state: PyState
): InterpretedSymbolicPythonObject {
    val ctx = state.ctx
    val element = modelHolder.model.readArrayIndex(
        address,
        indexExpr,
        ArrayType,
        ctx.addressSort
    ) as UConcreteHeapRef
    val actualAddress = if (element.address == 0) {
        defaultElement(ctx)
    } else {
        element
    }
    return if (isStaticHeapRef(actualAddress)) {
        val type = state.memory.typeStreamOf(actualAddress).first()
        require(type is ConcretePythonType)
        InterpretedAllocatedOrStaticSymbolicPythonObject(actualAddress, type, typeSystem)
    } else {
        InterpretedInputSymbolicPythonObject(actualAddress, modelHolder, typeSystem)
    }
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
    myAssert(ctx, with(ctx.ctx) { mkHeapRefEq(value.address, nullRef).not() or mkHeapRefEq(defaultElement(ctx), nullRef) })
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
