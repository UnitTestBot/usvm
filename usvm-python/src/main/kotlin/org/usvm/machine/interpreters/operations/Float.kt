package org.usvm.machine.interpreters.operations

import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.UPythonContext
import org.usvm.machine.symbolicobjects.*

private fun <RES_SORT: KSort> createBinaryFloatOp(
    op: (UPythonContext, UExpr<KRealSort>, UExpr<KRealSort>) -> UExpr<RES_SORT>?
): (ConcolicRunContext, UninterpretedSymbolicPythonObject, UninterpretedSymbolicPythonObject) -> UninterpretedSymbolicPythonObject? = { ctx, left, right ->
    if (ctx.curState == null)
        null
    else with (ctx.ctx) {
        val typeSystem = ctx.typeSystem
        val possibleTypes = listOf(typeSystem.pythonFloat, typeSystem.pythonInt, typeSystem.pythonBool)
        addPossibleSupertypes(ctx, listOf(left, right), possibleTypes)
        /*if (left.getTypeIfDefined(ctx) != typeSystem.pythonFloat)
            myFork(ctx, left.evalIs(ctx, typeSystem.pythonFloat))
        if (right.getTypeIfDefined(ctx) != typeSystem.pythonFloat)
            myFork(ctx, right.evalIs(ctx, typeSystem.pythonFloat))*/
        op(
            ctx.ctx,
            left.getToFloatContent(ctx) ?: return@with null,
            right.getToFloatContent(ctx) ?: return@with null
        )?.let {
            @Suppress("unchecked_cast")
            when (it.sort) {
                realSort -> constructFloat(ctx, it as UExpr<KRealSort>)
                intSort -> constructInt(ctx, it as UExpr<KIntSort>)
                boolSort -> constructBool(ctx, it as UBoolExpr)
                else -> error("Bad return sort of float operation: ${it.sort}")
            }
        }
    }
}

fun handlerGTFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { left gt right } } (x, y, z)
fun handlerLTFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { left lt right } } (x, y, z)
fun handlerEQFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { left eq right } } (x, y, z)
fun handlerNEFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { left neq right } } (x, y, z)
fun handlerGEFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { left ge right} } (x, y, z)
fun handlerLEFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { left le right } } (x, y, z)
fun handlerADDFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { mkArithAdd(left, right) } } (x, y, z)
fun handlerSUBFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { mkArithSub(left, right) } } (x, y, z)
fun handlerMULFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { mkArithMul(left, right) } } (x, y, z)
fun handlerDIVFloatKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryFloatOp { ctx, left, right -> with(ctx) { mkArithDiv(left, right) } } (x, y, z)

fun castFloatToInt(
    ctx: ConcolicRunContext,
    float: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject {
    require(float.getTypeIfDefined(ctx) == ctx.typeSystem.pythonFloat)
    val value = float.getFloatContent(ctx)
    val intValue = ctx.ctx.mkRealToInt(value)
    return constructInt(ctx, intValue)
}
