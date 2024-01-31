package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.*
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.interpreters.symbolic.operations.basic.myAssert
import org.usvm.machine.interpreters.symbolic.operations.basic.myAssertOnState
import org.usvm.machine.symbolicobjects.*
import org.usvm.types.first

const val DEFAULT_ELEMENT_INDEX = -1

fun UninterpretedSymbolicPythonObject.readArrayLength(ctx: ConcolicRunContext): UExpr<KIntSort> {
    val type = getFirstTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val result = ctx.curState!!.memory.readArrayLength(address, ArrayType, ctx.ctx.intSort)
    myAssert(ctx, ctx.ctx.mkArithGe(result, ctx.ctx.mkIntNum(0)))
    return result
}

fun InterpretedInputSymbolicPythonObject.readArrayLength(ctx: PyContext): UExpr<KIntSort> {
    require(getFirstType() != null && getFirstType() is ArrayLikeConcretePythonType)
    return modelHolder.model.readArrayLength(address, ArrayType, ctx.intSort)
}

private fun makeElementConstraint(
    ctx: PyContext,
    type: ArrayLikeConcretePythonType,
    array: UninterpretedSymbolicPythonObject,
    elem: UninterpretedSymbolicPythonObject,
    state: PyState
): UBoolExpr {
    val typeSystem = type.owner
    var cond = type.elementConstraints.fold(ctx.trueExpr as UBoolExpr) { acc, constraint ->
        ctx.mkAnd(acc, constraint.applyUninterpreted(array, elem, state))
    }
    if (type.innerType != null && typeSystem.isNonNullType(type.innerType)) {
        cond = with(ctx) { cond and mkHeapRefEq(elem.address, nullRef).not() }
    }
    return cond
}

private fun UninterpretedSymbolicPythonObject.defaultElementAsserts(state: PyState): UBoolExpr {
    val type = getTypeIfDefined(state)
    require(type != null && type is ArrayLikeConcretePythonType && type.innerType != null)
    val ctx = state.ctx
    val result = state.memory.readArrayIndex(
        address,
        ctx.mkIntNum(DEFAULT_ELEMENT_INDEX),
        ArrayType,
        ctx.addressSort
    )
    val obj = UninterpretedSymbolicPythonObject(result, state.typeSystem)
    val array = this
    return makeElementConstraint(ctx, type, array, obj, state)
}

fun UninterpretedSymbolicPythonObject.makeDefaultElementAsserts(state: PyState): PyState? {
    val cond = defaultElementAsserts(state)
    return myAssertOnState(state, cond)
}

fun UninterpretedSymbolicPythonObject.makeDefaultElementAsserts(ctx: ConcolicRunContext) {
    require(ctx.curState != null)
    val cond = defaultElementAsserts(ctx.curState!!)
    return myAssert(ctx, cond)
}

/*fun InterpretedInputSymbolicPythonObject.defaultElement(ctx: PyContext): UConcreteHeapRef =
    modelHolder.model.readArrayIndex(
        address,
        ctx.mkIntNum(DEFAULT_ELEMENT_INDEX),
        ArrayType,
        ctx.addressSort
    ) as UConcreteHeapRef
*/

fun UninterpretedSymbolicPythonObject.readArrayElement(
    ctx: ConcolicRunContext,
    index: UExpr<KIntSort>
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val elemAddress = ctx.curState!!.memory.readArrayIndex(address, index, ArrayType, ctx.ctx.addressSort)
    val elem = UninterpretedSymbolicPythonObject(elemAddress, typeSystem)
    if (isAllocatedObject(ctx))
        return elem
    val cond = makeElementConstraint(ctx.ctx, type, this, elem, ctx.curState!!)
    myAssert(ctx, cond)
    /*val actualAddress = if (type.innerType != null) {
        with(ctx.ctx) {
            mkIte(mkHeapRefEq(elemAddress, nullRef), defaultElement(ctx), elemAddress)
        }
    } else {
        elemAddress
    }*/
    return UninterpretedSymbolicPythonObject(elemAddress, typeSystem)
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
    /*val actualAddress = if (element.address == 0) {
        val type = modelHolder.model.getConcreteType(address)
        if (type != null && type is ArrayLikeConcretePythonType && type.innerType != null) {
            defaultElement(ctx)
        } else {
            element
        }
    } else {
        element
    }*/
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
    value: UninterpretedSymbolicPythonObject
) {
    require(ctx.curState != null)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    if (!isAllocatedObject(ctx)) {
        val cond = makeElementConstraint(ctx.ctx, type, this, value, ctx.curState!!)
        myAssert(ctx, cond)
    }
    /*if (type.innerType != null) {
        myAssert(
            ctx,
            with(ctx.ctx) { mkHeapRefEq(value.address, nullRef).not() or mkHeapRefEq(defaultElement(ctx), nullRef) }
        )
    }*/
    ctx.curState!!.memory.writeArrayIndex(
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
    on: UninterpretedSymbolicPythonObject
) {
    require(ctx.curState != null)
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    type.elementConstraints.forEach { constraint ->
        on.addSupertypeSoft(ctx, HasElementConstraint(constraint))
    }
}
