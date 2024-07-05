package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
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
import org.usvm.isFalse
import org.usvm.isStaticHeapRef
import org.usvm.isTrue
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.DictContents
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.IntDictType
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.RefDictType
import org.usvm.memory.UMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.types.first

fun UninterpretedSymbolicPythonObject.dictIsEmpty(ctx: ConcolicRunContext): UBoolExpr {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    return ctx.ctx.mkNot(ctx.extractCurState().memory.readField(address, DictContents.isNotEmpty, ctx.ctx.boolSort))
}

fun UninterpretedSymbolicPythonObject.setDictNotEmpty(ctx: ConcolicRunContext) {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertypeSoft(ctx, typeSystem.pythonDict)
    ctx.extractCurState().memory.writeField(
        address,
        DictContents.isNotEmpty,
        ctx.ctx.boolSort,
        ctx.ctx.trueExpr,
        ctx.ctx.trueExpr
    )
}

fun UninterpretedSymbolicPythonObject.readDictRefElement(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val resultAddress = ctx.extractCurState()
        .symbolicObjectMapGet(address, key.address, RefDictType, ctx.ctx.addressSort)
    return UninterpretedSymbolicPythonObject(resultAddress, typeSystem)
}

fun UninterpretedSymbolicPythonObject.dictContainsRef(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject,
): UBoolExpr {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val contains = ctx.extractCurState().symbolicObjectMapContains(address, key.address, RefDictType)
    return with(ctx.ctx) {
        dictIsEmpty(ctx).not() and contains
    }
}

fun UninterpretedSymbolicPythonObject.writeDictRefElement(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject,
) {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertypeSoft(ctx, typeSystem.pythonDict)
    setDictNotEmpty(ctx)
    ctx.extractCurState().symbolicObjectMapPut(address, key.address, value.address, RefDictType, ctx.ctx.addressSort)
}

fun UninterpretedSymbolicPythonObject.readDictIntElement(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>,
): UninterpretedSymbolicPythonObject {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val lvalue = UMapEntryLValue(ctx.ctx.intSort, ctx.ctx.addressSort, address, key, IntDictType, USizeExprKeyInfo())
    val resultAddress = ctx.extractCurState().memory.read(lvalue)
    return UninterpretedSymbolicPythonObject(resultAddress, typeSystem)
}

fun UninterpretedSymbolicPythonObject.dictContainsInt(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>,
): UBoolExpr {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonDict)
    val lvalue = USetEntryLValue(ctx.ctx.intSort, address, key, IntDictType, USizeExprKeyInfo())
    val contains = ctx.extractCurState().memory.read(lvalue)
    return with(ctx.ctx) {
        dictIsEmpty(ctx).not() and contains
    }
}

fun UninterpretedSymbolicPythonObject.writeDictIntElement(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>,
    value: UninterpretedSymbolicPythonObject,
) {
    requireNotNull(ctx.curState)
    val typeSystem = ctx.typeSystem
    addSupertypeSoft(ctx, typeSystem.pythonDict)
    setDictNotEmpty(ctx)
    val lvalue = UMapEntryLValue(ctx.ctx.intSort, ctx.ctx.addressSort, address, key, IntDictType, USizeExprKeyInfo())
    ctx.extractCurState().memory.write(lvalue, value.address, ctx.ctx.trueExpr)
    val lvalueSet = USetEntryLValue(ctx.ctx.intSort, address, key, IntDictType, USizeExprKeyInfo())
    ctx.extractCurState().memory.write(lvalueSet, ctx.ctx.trueExpr, ctx.ctx.trueExpr)
    // TODO: size?
}

fun InterpretedInputSymbolicPythonObject.dictIsEmpty(ctx: PyContext): Boolean {
    val field = modelHolder.model.readField(address, DictContents.isNotEmpty, ctx.boolSort)
    return modelHolder.model.eval(field).isFalse
}

private fun InterpretedInputSymbolicPythonObject.constructResultObject(
    resultAddress: UConcreteHeapRef,
    memory: UMemory<PythonType, PyCallable>,
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
    memory: UMemory<PythonType, PyCallable>,
): InterpretedSymbolicPythonObject {
    val lvalue = URefMapEntryLValue(ctx.addressSort, address, key.address, RefDictType)
    val elemAddress = modelHolder.model.read(lvalue) as UConcreteHeapRef
    return constructResultObject(elemAddress, memory)
}

fun InterpretedInputSymbolicPythonObject.dictContainsRef(
    ctx: PyContext,
    key: InterpretedSymbolicPythonObject,
): Boolean {
    val lvalue = URefSetEntryLValue(address, key.address, RefDictType)
    val result = modelHolder.model.read(lvalue)
    return !dictIsEmpty(ctx) && result.isTrue
}

fun InterpretedInputSymbolicPythonObject.readDictIntElement(
    ctx: PyContext,
    key: KInterpretedValue<KIntSort>,
    memory: UMemory<PythonType, PyCallable>,
): InterpretedSymbolicPythonObject {
    val lvalue = UMapEntryLValue(ctx.intSort, ctx.addressSort, address, key, IntDictType, USizeExprKeyInfo())
    val resultAddress = modelHolder.model.read(lvalue) as UConcreteHeapRef
    return constructResultObject(resultAddress, memory)
}

fun InterpretedInputSymbolicPythonObject.dictContainsInt(
    ctx: PyContext,
    key: KInterpretedValue<KIntSort>,
): Boolean {
    val lvalue = USetEntryLValue(ctx.intSort, address, key, IntDictType, USizeExprKeyInfo())
    return !dictIsEmpty(ctx) && modelHolder.model.read(lvalue).isTrue
}
