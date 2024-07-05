package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.typeStreamOf
import org.usvm.api.writeArrayIndex
import org.usvm.isStaticHeapRef
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.symbolic.operations.basic.pyAssert
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.ArrayLikeConcretePythonType
import org.usvm.machine.types.ArrayType
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.HasElementConstraint
import org.usvm.types.first

fun UninterpretedSymbolicPythonObject.readArrayLength(ctx: ConcolicRunContext): UExpr<KIntSort> {
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val result = ctx.extractCurState().memory.readArrayLength(address, ArrayType, ctx.ctx.intSort)
    pyAssert(ctx, ctx.ctx.mkArithGe(result, ctx.ctx.mkIntNum(0)))
    return result
}

fun InterpretedInputSymbolicPythonObject.readArrayLength(ctx: PyContext): UExpr<KIntSort> {
    require(getConcreteType() != null && getConcreteType() is ArrayLikeConcretePythonType)
    return modelHolder.model.readArrayLength(address, ArrayType, ctx.intSort)
}

fun UninterpretedSymbolicPythonObject.readArrayElement(
    ctx: ConcolicRunContext,
    index: UExpr<KIntSort>,
): UninterpretedSymbolicPythonObject {
    requireNotNull(ctx.curState)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val elemAddress = ctx.extractCurState().memory.readArrayIndex(address, index, ArrayType, ctx.ctx.addressSort)
    val elem = UninterpretedSymbolicPythonObject(elemAddress, typeSystem)
    if (isAllocatedObject(ctx)) {
        return elem
    }
    val cond = type.elementConstraints.fold(ctx.ctx.trueExpr as UBoolExpr) { acc, constraint ->
        ctx.ctx.mkAnd(acc, constraint.applyUninterpreted(this, elem, ctx))
    }
    pyAssert(ctx, cond)
    return UninterpretedSymbolicPythonObject(elemAddress, typeSystem)
}

fun InterpretedInputSymbolicPythonObject.readArrayElement(
    indexExpr: KInterpretedValue<KIntSort>,
    state: PyState,
): InterpretedSymbolicPythonObject {
    val ctx = state.ctx
    val element = modelHolder.model.readArrayIndex(
        address,
        indexExpr,
        ArrayType,
        ctx.addressSort
    ) as UConcreteHeapRef
    return if (isStaticHeapRef(element)) {
        val type = state.memory.typeStreamOf(element).first()
        require(type is ConcretePythonType)
        InterpretedAllocatedOrStaticSymbolicPythonObject(element, type, typeSystem)
    } else {
        InterpretedInputSymbolicPythonObject(element, modelHolder, typeSystem)
    }
}

fun UninterpretedSymbolicPythonObject.writeArrayElement(
    ctx: ConcolicRunContext,
    index: UExpr<KIntSort>,
    value: UninterpretedSymbolicPythonObject,
) {
    requireNotNull(ctx.curState)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    if (!isAllocatedObject(ctx)) {
        val cond = type.elementConstraints.fold(ctx.ctx.trueExpr as UBoolExpr) { acc, constraint ->
            ctx.ctx.mkAnd(acc, constraint.applyUninterpreted(this, value, ctx))
        }
        pyAssert(ctx, cond)
    }
    ctx.extractCurState().memory.writeArrayIndex(
        address,
        index,
        ArrayType,
        ctx.ctx.addressSort,
        value.address,
        ctx.ctx.trueExpr
    )
}

fun UninterpretedSymbolicPythonObject.extendArrayConstraints(
    ctx: ConcolicRunContext,
    on: UninterpretedSymbolicPythonObject,
) {
    requireNotNull(ctx.curState)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    type.elementConstraints.forEach { constraint ->
        on.addSupertypeSoft(ctx, HasElementConstraint(constraint))
    }
}
