package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.collection.field.UFieldLValue
import org.usvm.constraints.UPathConstraints
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.memory.FloatUninterpretedContent
import org.usvm.machine.symbolicobjects.memory.SliceUninterpretedField
import org.usvm.machine.symbolicobjects.memory.setFloatContent
import org.usvm.machine.symbolicobjects.memory.setIntContent
import org.usvm.machine.symbolicobjects.memory.setListIteratorContent
import org.usvm.machine.symbolicobjects.memory.setRangeContent
import org.usvm.machine.symbolicobjects.memory.setRangeIteratorContent
import org.usvm.machine.symbolicobjects.memory.setSliceStart
import org.usvm.machine.symbolicobjects.memory.setSliceStep
import org.usvm.machine.symbolicobjects.memory.setSliceStop
import org.usvm.machine.symbolicobjects.memory.setTupleIteratorContent
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.memory.UMemory
import org.usvm.memory.URegisterStackLValue

fun constructInputObject(
    stackIndex: Int,
    type: PythonType,
    ctx: PyContext,
    memory: UMemory<PythonType, PyCallable>,
    pathConstraints: UPathConstraints<PythonType>,
    typeSystem: PythonTypeSystem,
): UninterpretedSymbolicPythonObject {
    @Suppress("unchecked_cast")
    val address = memory.read(URegisterStackLValue(ctx.addressSort, stackIndex)) as UExpr<UAddressSort>
    pathConstraints += ctx.mkNot(ctx.mkHeapRefEq(address, ctx.nullRef))
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += result.evalIs(ctx, pathConstraints.typeConstraints, type)
    return result
}

fun constructEmptyAllocatedObject(
    ctx: PyContext,
    memory: UMemory<PythonType, PyCallable>,
    typeSystem: PythonTypeSystem,
    type: ConcretePythonType,
): UninterpretedSymbolicPythonObject {
    val address = memory.allocConcrete(type)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setMinimalTimeOfCreation(ctx, memory)
    }
}

fun constructEmptyStaticObject(
    ctx: PyContext,
    memory: UMemory<PythonType, PyCallable>,
    typeSystem: PythonTypeSystem,
    type: ConcretePythonType,
): UninterpretedSymbolicPythonObject {
    val address = memory.allocStatic(type)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setMinimalTimeOfCreation(ctx, memory)
    }
}

fun constructInt(context: ConcolicRunContext, expr: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)
    val typeSystem = context.typeSystem
    val address = context.extractCurState().memory.allocConcrete(typeSystem.pythonInt)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setIntContent(context, expr)
    result.setMinimalTimeOfCreation(context.ctx, context.extractCurState().memory)
    return result
}

fun constructFloat(context: ConcolicRunContext, expr: FloatUninterpretedContent): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)
    val typeSystem = context.typeSystem
    val address = context.extractCurState().memory.allocConcrete(typeSystem.pythonFloat)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setFloatContent(context, expr)
    result.setMinimalTimeOfCreation(context.ctx, context.extractCurState().memory)
    return result
}


fun constructBool(context: ConcolicRunContext, expr: UBoolExpr): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)

    val trueObj = context.extractCurState().preAllocatedObjects.trueObject
    val falseObj = context.extractCurState().preAllocatedObjects.falseObject
    val address = context.ctx.mkIte(expr, trueObj.address, falseObj.address)
    return UninterpretedSymbolicPythonObject(address, context.typeSystem)
}

fun constructInitialBool(
    ctx: PyContext,
    memory: UMemory<PythonType, PyCallable>,
    pathConstraints: UPathConstraints<PythonType>,
    typeSystem: PythonTypeSystem,
    expr: UExpr<KBoolSort>,
): UninterpretedSymbolicPythonObject {
    val address = memory.allocStatic(typeSystem.pythonBool)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += pathConstraints.typeConstraints.evalIsSubtype(address, typeSystem.pythonBool)
    val lvalue = UFieldLValue(expr.sort, address, BoolContents.content)
    memory.write(lvalue, expr, ctx.trueExpr)
    result.setMinimalTimeOfCreation(ctx, memory)
    return result
}

fun constructListIterator(
    context: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)
    val typeSystem = context.typeSystem
    val address = context.extractCurState().memory.allocConcrete(typeSystem.pythonListIteratorType)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setListIteratorContent(context, list)
    result.setMinimalTimeOfCreation(context.ctx, context.extractCurState().memory)
    return result
}

fun constructTupleIterator(
    context: ConcolicRunContext,
    tuple: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)
    val typeSystem = context.typeSystem
    val address = context.extractCurState().memory.allocConcrete(typeSystem.pythonTupleIteratorType)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setTupleIteratorContent(context, tuple)
        it.setMinimalTimeOfCreation(context.ctx, context.extractCurState().memory)
    }
}

fun constructRange(
    context: ConcolicRunContext,
    start: UExpr<KIntSort>,
    stop: UExpr<KIntSort>,
    step: UExpr<KIntSort>,
): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)
    val typeSystem = context.typeSystem
    val address = context.extractCurState().memory.allocConcrete(typeSystem.pythonRange)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setRangeContent(context, start, stop, step)
        it.setMinimalTimeOfCreation(context.ctx, context.extractCurState().memory)
    }
}

fun constructRangeIterator(
    context: ConcolicRunContext,
    range: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject {
    requireNotNull(context.curState)
    val typeSystem = context.typeSystem
    val address = context.extractCurState().memory.allocConcrete(typeSystem.pythonRangeIterator)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setRangeIteratorContent(context, range)
        it.setMinimalTimeOfCreation(context.ctx, context.extractCurState().memory)
    }
}

fun constructSlice(
    ctx: ConcolicRunContext,
    start: SliceUninterpretedField,
    stop: SliceUninterpretedField,
    step: SliceUninterpretedField,
): UninterpretedSymbolicPythonObject {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    val address = ctx.extractCurState().memory.allocConcrete(typeSystem.pythonSlice)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setSliceStart(ctx, start)
        it.setSliceStop(ctx, stop)
        it.setSliceStep(ctx, step)
        it.setMinimalTimeOfCreation(ctx.ctx, ctx.extractCurState().memory)
    }
}
