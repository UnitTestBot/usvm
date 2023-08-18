package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemoryBase

fun constructInputObject(
    stackIndex: Int,
    type: PythonType,
    ctx: UContext,
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    pathConstraints: UPathConstraints<PythonType, UPythonContext>,
    typeSystem: PythonTypeSystem
): UninterpretedSymbolicPythonObject {
    @Suppress("unchecked_cast")
    val address = memory.read(URegisterLValue(ctx.addressSort, stackIndex)) as UExpr<UAddressSort>
    pathConstraints += ctx.mkNot(ctx.mkHeapRefEq(address, ctx.nullRef))
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += result.evalIs(ctx, pathConstraints.typeConstraints, type, null)
    return result
}

fun constructNone(
    memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    typeSystem: PythonTypeSystem
): UninterpretedSymbolicPythonObject {
    val address = memory.alloc(typeSystem.pythonNoneType)
    return UninterpretedSymbolicPythonObject(address, typeSystem)
}

fun constructInt(context: ConcolicRunContext, expr: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.alloc(typeSystem.pythonInt)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setIntContent(context, expr)
    return result
}

fun constructBool(context: ConcolicRunContext, expr: UExpr<KBoolSort>): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.alloc(typeSystem.pythonBool)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setBoolContent(context, expr)
    return result
}

fun constructListIterator(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.alloc(typeSystem.pythonListIteratorType)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setListIteratorContent(context, list)
    return result
}