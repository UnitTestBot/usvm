package org.usvm.interpreter.symbolicobjects

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.*
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.language.types.pythonBool
import org.usvm.language.types.pythonInt
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
    val result = UninterpretedSymbolicPythonObject(address)
    pathConstraints += memory.types.evalIs(address, type)
    return result
}


fun constructInt(context: ConcolicRunContext, expr: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    val address = context.curState.memory.alloc(pythonInt)
    val result = UninterpretedSymbolicPythonObject(address)
    result.setIntContent(context, expr)
    return result
}

fun constructBool(context: ConcolicRunContext, expr: UExpr<KBoolSort>): UninterpretedSymbolicPythonObject {
    val address = context.curState.memory.alloc(pythonBool)
    val result = UninterpretedSymbolicPythonObject(address)
    result.setBoolContent(context, expr)
    return result
}