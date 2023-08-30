package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.collection.field.UFieldLValue
import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemory
import org.usvm.memory.URegisterStackLValue

fun constructInputObject(
    stackIndex: Int,
    type: PythonType,
    ctx: UContext,
    memory: UMemory<PythonType, PythonCallable>,
    pathConstraints: UPathConstraints<PythonType, UPythonContext>,
    typeSystem: PythonTypeSystem
): UninterpretedSymbolicPythonObject {
    @Suppress("unchecked_cast")
    val address = memory.read(URegisterStackLValue(ctx.addressSort, stackIndex)) as UExpr<UAddressSort>
    pathConstraints += ctx.mkNot(ctx.mkHeapRefEq(address, ctx.nullRef))
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += result.evalIsSoft(ctx, pathConstraints.typeConstraints, type)
    return result
}

fun constructNone(
    memory: UMemory<PythonType, PythonCallable>,
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

fun constructBool(context: ConcolicRunContext, expr: UBoolExpr): UninterpretedSymbolicPythonObject {
    require(context.curState != null)

    val trueObj = context.curState!!.preAllocatedObjects.trueObject
    val falseObj = context.curState!!.preAllocatedObjects.falseObject
    val address = context.ctx.mkIte(expr, trueObj.address, falseObj.address)
    return UninterpretedSymbolicPythonObject(address, context.typeSystem)
}

fun constructInitialBool(
    ctx: UContext,
    memory: UMemory<PythonType, PythonCallable>,
    pathConstraints: UPathConstraints<PythonType, UPythonContext>,
    typeSystem: PythonTypeSystem,
    expr: UExpr<KBoolSort>
): UninterpretedSymbolicPythonObject {
    val address = memory.alloc(typeSystem.pythonBool)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += result.evalIsSoft(ctx, pathConstraints.typeConstraints, typeSystem.pythonBool)
    val lvalue = UFieldLValue(expr.sort, address, BoolContents.content)
    memory.write(lvalue, expr, ctx.trueExpr)
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

fun constructTupleIterator(context: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.alloc(typeSystem.pythonTupleIteratorType)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also { it.setTupleIteratorContent(context, tuple) }
}

fun constructRange(context: ConcolicRunContext, start: UExpr<KIntSort>, stop: UExpr<KIntSort>, step: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.alloc(typeSystem.pythonRange)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setRangeContent(context, start, stop, step)
    }
}

fun constructRangeIterator(context: ConcolicRunContext, range: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.alloc(typeSystem.pythonRangeIterator)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setRangeIteratorContent(context, range)
    }
}