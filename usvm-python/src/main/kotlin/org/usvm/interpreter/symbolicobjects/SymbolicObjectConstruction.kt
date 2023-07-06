package org.usvm.interpreter.symbolicobjects

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.memory.UMemoryBase

fun constructInputObject(
    stackIndex: Int,
    type: ConcretePythonType,
    ctx: UContext,
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    pathConstraints: UPathConstraints<PythonType>
): UninterpretedSymbolicPythonObject {
    @Suppress("unchecked_cast")
    val address = memory.read(URegisterLValue(ctx.addressSort, stackIndex)) as UExpr<UAddressSort>
    pathConstraints += ctx.mkNot(ctx.mkHeapRefEq(address, ctx.nullRef))
    val result = UninterpretedSymbolicPythonObject(address, memory, ctx)
    result.castToConcreteType(type)
    return result
}

fun <SORT: USort> constructObject(
    expr: UExpr<SORT>,
    concretePythonType: ConcretePythonType,
    ctx: UContext,
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>
): UninterpretedSymbolicPythonObject {
    val address = memory.alloc(concretePythonType)
    val result = UninterpretedSymbolicPythonObject(address, memory, ctx)
    result.setContent(expr, concretePythonType)
    return result
}