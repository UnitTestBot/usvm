package org.usvm.machine.interpreters.operations

import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.machine.symbolicobjects.*

private fun <RES_SORT: KSort> createBinaryIntOp(
    op: (ConcolicRunContext, UExpr<KIntSort>, UExpr<KIntSort>) -> UExpr<RES_SORT>?
): (ConcolicRunContext, UninterpretedSymbolicPythonObject, UninterpretedSymbolicPythonObject) -> UninterpretedSymbolicPythonObject? = { ctx, left, right ->
    if (ctx.curState == null)
        null
    else with (ctx.ctx) {
        val typeSystem = ctx.typeSystem
        val possibleTypes = listOf(typeSystem.pythonInt, typeSystem.pythonBool)
        addPossibleSupertypes(ctx, listOf(left, right), possibleTypes)
        op(
            ctx,
            left.getToIntContent(ctx) ?: return@with null,
            right.getToIntContent(ctx) ?: return@with null
        )?.let {
            @Suppress("unchecked_cast")
            when (it.sort) {
                intSort -> constructInt(ctx, it as UExpr<KIntSort>)
                boolSort -> constructBool(ctx, it as UBoolExpr)
                realSort -> constructFloat(ctx, mkUninterpretedFloatWithValue(ctx.ctx, it as UExpr<KRealSort>))
                else -> error("Bad return sort of int operation: ${it.sort}")
            }
        }
    }
}

fun handlerGTLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left gt right } } (x, y, z)
fun handlerLTLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left lt right } } (x, y, z)
fun handlerEQLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left eq right } } (x, y, z)
fun handlerNELongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left neq right } } (x, y, z)
fun handlerGELongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left ge right } } (x, y, z)
fun handlerLELongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left le right } } (x, y, z)
fun handlerADDLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithAdd(left, right) } (x, y, z)
fun handlerSUBLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithSub(left, right) } (x, y, z)
fun handlerMULLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithMul(left, right) } (x, y, z)
fun handlerDIVLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right ->
        with (ctx.ctx) {
            myFork(ctx, right eq mkIntNum(0))
            if (ctx.modelHolder.model.eval(right eq mkIntNum(0)).isTrue)
                null
            else
                mkArithDiv(left, right)
        }
    } (x, y, z)
fun handlerREMLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkIntMod(left, right) } (x, y, z)
@Suppress("unused_parameter")
fun handlerPOWLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = null  // TODO
    //createBinaryIntOp { ctx, left, right ->
    //    if (right is KIntNumExpr) ctx.mkArithPower(left, right) else null
    //} (x, y, z)
fun handlerTrueDivLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right ->
        with (ctx.ctx) {
            myFork(ctx, right eq mkIntNum(0))
            if (ctx.modelHolder.model.eval(right eq mkIntNum(0)).isTrue)
                null
            else
                mkArithDiv(mkIntToReal(left), mkIntToReal(right))
        }
    } (x, y, z)

fun handlerIntCastKt(
    ctx: ConcolicRunContext,
    arg: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null)
        return null
    val typeSystem = ctx.typeSystem
    val type = arg.getTypeIfDefined(ctx) ?: return null
    return when (type) {
        typeSystem.pythonInt -> arg
        typeSystem.pythonBool -> constructInt(ctx, arg.getToIntContent(ctx)!!)
        typeSystem.pythonFloat -> castFloatToInt(ctx, arg)
        else -> null
    }
}