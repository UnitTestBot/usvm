package org.usvm.machine.symbolicobjects.memory

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.typeStreamOf
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.isTrue
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.ObjectDictType
import org.usvm.machine.types.PythonType
import org.usvm.memory.UMemory
import org.usvm.types.first

fun UninterpretedSymbolicPythonObject.getFieldValue(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject {
    requireNotNull(ctx.curState)
    name.addSupertype(ctx, typeSystem.pythonStr)
    val addr = ctx.extractCurState().symbolicObjectMapGet(address, name.address, ObjectDictType, ctx.ctx.addressSort)
    return UninterpretedSymbolicPythonObject(addr, typeSystem)
}

fun UninterpretedSymbolicPythonObject.setFieldValue(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    requireNotNull(ctx.curState)
    name.addSupertypeSoft(ctx, typeSystem.pythonStr)
    ctx.extractCurState()
        .symbolicObjectMapPut(address, name.address, value.address, ObjectDictType, ctx.ctx.addressSort)
}

fun UninterpretedSymbolicPythonObject.containsField(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject,
): UBoolExpr {
    requireNotNull(ctx.curState)
    name.addSupertype(ctx, typeSystem.pythonStr)
    return ctx.extractCurState().symbolicObjectMapContains(address, name.address, ObjectDictType)
}

fun InterpretedInputSymbolicPythonObject.containsField(
    name: InterpretedSymbolicPythonObject,
): Boolean {
    require(!isAllocatedConcreteHeapRef(name.address))
    val result = modelHolder.model.read(URefSetEntryLValue(address, name.address, ObjectDictType))
    return result.isTrue
}

fun InterpretedInputSymbolicPythonObject.getFieldValue(
    ctx: PyContext,
    name: InterpretedSymbolicPythonObject,
    memory: UMemory<PythonType, PyCallable>,
): InterpretedSymbolicPythonObject {
    require(!isAllocatedConcreteHeapRef(name.address))
    val result = modelHolder.model.read(URefMapEntryLValue(ctx.addressSort, address, name.address, ObjectDictType))
    require((result as UConcreteHeapRef).address <= 0)
    return if (!isStaticHeapRef(result)) {
        InterpretedInputSymbolicPythonObject(result, modelHolder, typeSystem)
    } else {
        val type = memory.typeStreamOf(result).first()
        require(type is ConcretePythonType)
        InterpretedAllocatedOrStaticSymbolicPythonObject(result, type, typeSystem)
    }
}
