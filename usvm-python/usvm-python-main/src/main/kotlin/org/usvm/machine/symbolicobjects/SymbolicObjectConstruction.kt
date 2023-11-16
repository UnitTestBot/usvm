package org.usvm.machine.symbolicobjects

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.collection.field.UFieldLValue
import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.*
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemory
import org.usvm.memory.URegisterStackLValue

fun constructInputObject(
    stackIndex: Int,
    type: PythonType,
    ctx: UPythonContext,
    memory: UMemory<PythonType, PythonCallable>,
    pathConstraints: UPathConstraints<PythonType>,
    typeSystem: PythonTypeSystem,
    // preallocatedObjects: PreallocatedObjects
): UninterpretedSymbolicPythonObject {
    @Suppress("unchecked_cast")
    val address = memory.read(URegisterStackLValue(ctx.addressSort, stackIndex)) as UExpr<UAddressSort>
    pathConstraints += ctx.mkNot(ctx.mkHeapRefEq(address, ctx.nullRef))
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += result.evalIs(ctx, pathConstraints.typeConstraints, type)
    return result
}

fun constructEmptyAllocatedObject(
    ctx: UPythonContext,
    memory: UMemory<PythonType, PythonCallable>,
    typeSystem: PythonTypeSystem,
    type: ConcretePythonType
): UninterpretedSymbolicPythonObject {
    val address = memory.allocConcrete(type)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setMinimalTimeOfCreation(ctx, memory)
    }
}

fun constructEmptyStaticObject(
    ctx: UPythonContext,
    memory: UMemory<PythonType, PythonCallable>,
    typeSystem: PythonTypeSystem,
    type: ConcretePythonType
): UninterpretedSymbolicPythonObject {
    val address = memory.allocStatic(type)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setMinimalTimeOfCreation(ctx, memory)
    }
}

fun constructInt(context: ConcolicRunContext, expr: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.allocConcrete(typeSystem.pythonInt)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setIntContent(context, expr)
    result.setMinimalTimeOfCreation(context.ctx, context.curState!!.memory)
    return result
}

fun constructFloat(context: ConcolicRunContext, expr: FloatUninterpretedContent): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.allocConcrete(typeSystem.pythonFloat)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setFloatContent(context, expr)
    result.setMinimalTimeOfCreation(context.ctx, context.curState!!.memory)
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
    ctx: UPythonContext,
    memory: UMemory<PythonType, PythonCallable>,
    pathConstraints: UPathConstraints<PythonType>,
    typeSystem: PythonTypeSystem,
    expr: UExpr<KBoolSort>
): UninterpretedSymbolicPythonObject {
    val address = memory.allocStatic(typeSystem.pythonBool)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    pathConstraints += pathConstraints.typeConstraints.evalIsSubtype(address, typeSystem.pythonBool)
    val lvalue = UFieldLValue(expr.sort, address, BoolContents.content)
    memory.write(lvalue, expr, ctx.trueExpr)
    result.setMinimalTimeOfCreation(ctx, memory)
    return result
}

fun constructListIterator(context: ConcolicRunContext, list: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.allocConcrete(typeSystem.pythonListIteratorType)
    val result = UninterpretedSymbolicPythonObject(address, typeSystem)
    result.setListIteratorContent(context, list)
    result.setMinimalTimeOfCreation(context.ctx, context.curState!!.memory)
    return result
}

fun constructTupleIterator(context: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.allocConcrete(typeSystem.pythonTupleIteratorType)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setTupleIteratorContent(context, tuple)
        it.setMinimalTimeOfCreation(context.ctx, context.curState!!.memory)
    }
}

fun constructRange(context: ConcolicRunContext, start: UExpr<KIntSort>, stop: UExpr<KIntSort>, step: UExpr<KIntSort>): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.allocConcrete(typeSystem.pythonRange)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setRangeContent(context, start, stop, step)
        it.setMinimalTimeOfCreation(context.ctx, context.curState!!.memory)
    }
}

fun constructRangeIterator(context: ConcolicRunContext, range: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject {
    require(context.curState != null)
    val typeSystem = context.typeSystem
    val address = context.curState!!.memory.allocConcrete(typeSystem.pythonRangeIterator)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setRangeIteratorContent(context, range)
        it.setMinimalTimeOfCreation(context.ctx, context.curState!!.memory)
    }
}

fun constructSlice(
    ctx: ConcolicRunContext,
    start: SliceUninterpretedField,
    stop: SliceUninterpretedField,
    step: SliceUninterpretedField
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    val address = ctx.curState!!.memory.allocConcrete(typeSystem.pythonSlice)
    return UninterpretedSymbolicPythonObject(address, typeSystem).also {
        it.setSliceStart(ctx, start)
        it.setSliceStop(ctx, stop)
        it.setSliceStep(ctx, step)
        it.setMinimalTimeOfCreation(ctx.ctx, ctx.curState!!.memory)
    }
}