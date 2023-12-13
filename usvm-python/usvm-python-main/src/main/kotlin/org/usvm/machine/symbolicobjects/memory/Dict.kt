package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.readField
import org.usvm.api.typeStreamOf
import org.usvm.api.writeField
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonCallable
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.IntDictType
import org.usvm.language.types.PythonType
import org.usvm.language.types.RefDictType
import org.usvm.machine.PyContext
import org.usvm.machine.symbolicobjects.*
import org.usvm.memory.UMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.types.first


fun UninterpretedSymbolicPythonObject.dictIsEmpty(ctx: ConcolicRunContext): UBoolExpr {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    return ctx.ctx.mkNot(ctx.curState!!.memory.readField(address, DictContents.isNotEmpty, ctx.ctx.boolSort))
}

fun UninterpretedSymbolicPythonObject.setDictNotEmpty(ctx: ConcolicRunContext) {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertypeSoft(ctx, typeSystem.pythonDict)
    ctx.curState!!.memory.writeField(address, DictContents.isNotEmpty, ctx.ctx.boolSort, ctx.ctx.trueExpr, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.readDictRefElement(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val resultAddress = ctx.curState!!.symbolicObjectMapGet(address, key.address, RefDictType, ctx.ctx.addressSort)
    return UninterpretedSymbolicPythonObject(resultAddress, typeSystem)
}

fun UninterpretedSymbolicPythonObject.dictContainsRef(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
): UBoolExpr {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val contains = ctx.curState!!.symbolicObjectMapContains(address, key.address, RefDictType)
    return with(ctx.ctx) {
        dictIsEmpty(ctx).not() and contains
    }
}

fun UninterpretedSymbolicPythonObject.writeDictRefElement(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertypeSoft(ctx, typeSystem.pythonDict)
    setDictNotEmpty(ctx)
    ctx.curState!!.symbolicObjectMapPut(address, key.address, value.address, RefDictType, ctx.ctx.addressSort)
}

fun UninterpretedSymbolicPythonObject.readDictIntElement(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val lvalue = UMapEntryLValue(ctx.ctx.intSort, ctx.ctx.addressSort, address, key, IntDictType, USizeExprKeyInfo())
    val resultAddress = ctx.curState!!.memory.read(lvalue)
    return UninterpretedSymbolicPythonObject(resultAddress, typeSystem)
}

fun UninterpretedSymbolicPythonObject.dictContainsInt(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>
): UBoolExpr {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val lvalue = USetEntryLValue(ctx.ctx.intSort, address, key, IntDictType, USizeExprKeyInfo())
    val contains = ctx.curState!!.memory.read(lvalue)
    return with(ctx.ctx) {
        dictIsEmpty(ctx).not() and contains
    }
}

fun UninterpretedSymbolicPythonObject.writeDictIntElement(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>,
    value: UninterpretedSymbolicPythonObject
) {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertypeSoft(ctx, typeSystem.pythonDict)
    setDictNotEmpty(ctx)
    val lvalue = UMapEntryLValue(ctx.ctx.intSort, ctx.ctx.addressSort, address, key, IntDictType, USizeExprKeyInfo())
    ctx.curState!!.memory.write(lvalue, value.address, ctx.ctx.trueExpr)
    val lvalueSet = USetEntryLValue(ctx.ctx.intSort, address, key, IntDictType, USizeExprKeyInfo())
    ctx.curState!!.memory.write(lvalueSet, ctx.ctx.trueExpr, ctx.ctx.trueExpr)
    // TODO: size?
}

fun InterpretedInputSymbolicPythonObject.dictIsEmpty(ctx: PyContext): Boolean {
    val field = modelHolder.model.readField(address, DictContents.isNotEmpty, ctx.boolSort)
    return modelHolder.model.eval(field).isFalse
}

private fun InterpretedInputSymbolicPythonObject.constructResultObject(
    resultAddress: UConcreteHeapRef,
    memory: UMemory<PythonType, PythonCallable>
): InterpretedSymbolicPythonObject =
    if (isStaticHeapRef(resultAddress)) {
        val type = memory.typeStreamOf(resultAddress).first()
        require(type is ConcretePythonType)
        InterpretedAllocatedOrStaticSymbolicPythonObject(resultAddress, type, typeSystem)
    } else {
        InterpretedInputSymbolicPythonObject(resultAddress, modelHolder, typeSystem)
    }

fun InterpretedInputSymbolicPythonObject.readDictRefElement(
    ctx: PyContext,
    key: InterpretedSymbolicPythonObject,
    memory: UMemory<PythonType, PythonCallable>
): InterpretedSymbolicPythonObject {
    val lvalue = URefMapEntryLValue(ctx.addressSort, address, key.address, RefDictType)
    val elemAddress = modelHolder.model.read(lvalue) as UConcreteHeapRef
    return constructResultObject(elemAddress, memory)
}

fun InterpretedInputSymbolicPythonObject.dictContainsRef(
    ctx: PyContext,
    key: InterpretedSymbolicPythonObject
): Boolean {
    val lvalue = URefSetEntryLValue(address, key.address, RefDictType)
    val result = modelHolder.model.read(lvalue)
    return !dictIsEmpty(ctx) && result.isTrue
}

fun InterpretedInputSymbolicPythonObject.readDictIntElement(
    ctx: PyContext,
    key: KInterpretedValue<KIntSort>,
    memory: UMemory<PythonType, PythonCallable>
): InterpretedSymbolicPythonObject {
    val lvalue = UMapEntryLValue(ctx.intSort, ctx.addressSort, address, key, IntDictType, USizeExprKeyInfo())
    val resultAddress = modelHolder.model.read(lvalue) as UConcreteHeapRef
    return constructResultObject(resultAddress, memory)
}

fun InterpretedInputSymbolicPythonObject.dictContainsInt(
    ctx: PyContext,
    key: KInterpretedValue<KIntSort>
): Boolean {
    val lvalue = USetEntryLValue(ctx.intSort, address, key, IntDictType, USizeExprKeyInfo())
    return !dictIsEmpty(ctx) && modelHolder.model.read(lvalue).isTrue
}