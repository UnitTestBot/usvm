package org.usvm.machine.symbolicobjects.memory

import org.usvm.*
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.typeStreamOf
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PyCallable
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.ObjectDictType
import org.usvm.language.types.PythonType
import org.usvm.machine.PyContext
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.memory.UMemory
import org.usvm.types.first

fun UninterpretedSymbolicPythonObject.getFieldValue(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    name.addSupertype(ctx, typeSystem.pythonStr)
    val addr = ctx.curState!!.symbolicObjectMapGet(address, name.address, ObjectDictType, ctx.ctx.addressSort)
    return UninterpretedSymbolicPythonObject(addr, typeSystem)
}

fun UninterpretedSymbolicPythonObject.setFieldValue(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    require(ctx.curState != null)
    name.addSupertypeSoft(ctx, typeSystem.pythonStr)
    ctx.curState!!.symbolicObjectMapPut(address, name.address, value.address, ObjectDictType, ctx.ctx.addressSort)
}

fun UninterpretedSymbolicPythonObject.containsField(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject
): UBoolExpr {
    require(ctx.curState != null)
    name.addSupertype(ctx, typeSystem.pythonStr)
    return ctx.curState!!.symbolicObjectMapContains(address, name.address, ObjectDictType)
}

fun InterpretedInputSymbolicPythonObject.containsField(
    name: InterpretedSymbolicPythonObject
): Boolean {
    require(!isAllocatedConcreteHeapRef(name.address))
    val result = modelHolder.model.read(URefSetEntryLValue(address, name.address, ObjectDictType))
    return result.isTrue
}

fun InterpretedInputSymbolicPythonObject.getFieldValue(
    ctx: PyContext,
    name: InterpretedSymbolicPythonObject,
    memory: UMemory<PythonType, PyCallable>
): InterpretedSymbolicPythonObject {
    require(!isAllocatedConcreteHeapRef(name.address))
    val result = modelHolder.model.read(URefMapEntryLValue(ctx.addressSort, address, name.address, ObjectDictType))
    require((result as UConcreteHeapRef).address <= 0)
    return if (!isStaticHeapRef(result))
        InterpretedInputSymbolicPythonObject(result, modelHolder, typeSystem)
    else {
        val type = memory.typeStreamOf(result).first()
        require(type is ConcretePythonType)
        InterpretedAllocatedOrStaticSymbolicPythonObject(result, type, typeSystem)
    }
}
