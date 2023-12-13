package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.language.types.IntSetType
import org.usvm.language.types.RefSetType
import org.usvm.machine.PyContext
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.SetContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.memory.key.USizeExprKeyInfo


fun UninterpretedSymbolicPythonObject.setIsEmpty(ctx: ConcolicRunContext): UBoolExpr {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonSet)
    return ctx.ctx.mkNot(ctx.curState!!.memory.readField(address, SetContents.isNotEmpty, ctx.ctx.boolSort))
}

fun UninterpretedSymbolicPythonObject.makeSetNotEmpty(ctx: ConcolicRunContext) {
    require(ctx.curState != null)
    val typeSystem = ctx.typeSystem
    addSupertype(ctx, typeSystem.pythonSet)
    ctx.curState!!.memory.writeField(address, SetContents.isNotEmpty, ctx.ctx.boolSort, ctx.ctx.trueExpr, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.setContainsInt(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>
): UBoolExpr = with(ctx.ctx) {
    require(ctx.curState != null)
    val lvalue = USetEntryLValue(intSort, address, key, IntSetType, USizeExprKeyInfo())
    return setIsEmpty(ctx).not() and ctx.curState!!.memory.read(lvalue)
}

fun UninterpretedSymbolicPythonObject.addIntToSet(
    ctx: ConcolicRunContext,
    key: UExpr<KIntSort>
) = with(ctx.ctx) {
    require(ctx.curState != null)
    makeSetNotEmpty(ctx)
    val lvalue = USetEntryLValue(intSort, address, key, IntSetType, USizeExprKeyInfo())
    ctx.curState!!.memory.write(lvalue, trueExpr, trueExpr)
}

fun UninterpretedSymbolicPythonObject.setContainsRef(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
): UBoolExpr = with(ctx.ctx) {
    require(ctx.curState != null)
    val lvalue = URefSetEntryLValue(address, key.address, RefSetType)
    return setIsEmpty(ctx).not() and ctx.curState!!.memory.read(lvalue)
}

fun UninterpretedSymbolicPythonObject.addRefToSet(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    require(ctx.curState != null)
    makeSetNotEmpty(ctx)
    val lvalue = URefSetEntryLValue(address, key.address, RefSetType)
    ctx.curState!!.memory.write(lvalue, trueExpr, trueExpr)
}

fun InterpretedInputSymbolicPythonObject.setIsEmpty(ctx: PyContext): Boolean = with(ctx) {
    return modelHolder.model.readField(address, SetContents.isNotEmpty, boolSort).isFalse
}

fun InterpretedInputSymbolicPythonObject.setContainsInt(
    ctx: PyContext,
    key: KInterpretedValue<KIntSort>
): Boolean = with(ctx) {
    val lvalue = USetEntryLValue(intSort, address, key, IntSetType, USizeExprKeyInfo())
    return !setIsEmpty(ctx) && modelHolder.model.read(lvalue).isTrue
}

fun InterpretedInputSymbolicPythonObject.setContainsRef(
    ctx: PyContext,
    key: InterpretedSymbolicPythonObject
): Boolean {
    val lvalue = URefSetEntryLValue(address, key.address, RefSetType)
    return !setIsEmpty(ctx) && modelHolder.model.read(lvalue).isTrue
}