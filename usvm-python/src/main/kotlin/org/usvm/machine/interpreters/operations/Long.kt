package org.usvm.machine.interpreters.operations

import io.ksmt.sort.KIntSort
import io.ksmt.sort.KSort
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.language.types.pythonBool
import org.usvm.language.types.pythonInt

fun <RES_SORT: KSort> createBinaryIntOp(
    op: (UContext, UExpr<KIntSort>, UExpr<KIntSort>) -> UExpr<RES_SORT>?
): (ConcolicRunContext, UninterpretedSymbolicPythonObject, UninterpretedSymbolicPythonObject) -> UninterpretedSymbolicPythonObject? = { concolicContext, left, right ->
    if (concolicContext.curState == null)
        null
    else with (concolicContext.ctx) {
        myAssert(concolicContext, left.evalIs(concolicContext, pythonInt) or left.evalIs(concolicContext, pythonBool))
        myAssert(concolicContext, right.evalIs(concolicContext, pythonInt) or right.evalIs(concolicContext, pythonBool))
        op(
            concolicContext.ctx,
            left.getToIntContent(concolicContext) ?: return@with null,
            right.getToIntContent(concolicContext) ?: return@with null
        )?.let {
            @Suppress("unchecked_cast")
            when (it.sort) {
                intSort -> constructInt(concolicContext, it as UExpr<KIntSort>)
                boolSort -> constructBool(concolicContext, it as UBoolExpr)
                else -> TODO()
            }
        }
    }
}

fun handlerGTLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left gt right } } (x, y, z)
fun handlerLTLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left lt right } } (x, y, z)
fun handlerEQLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left eq right } } (x, y, z)
fun handlerNELongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left neq right } } (x, y, z)
fun handlerGELongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left ge right } } (x, y, z)
fun handlerLELongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left le right } } (x, y, z)
fun handlerADDLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithAdd(left, right) } (x, y, z)
fun handlerSUBLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithSub(left, right) } (x, y, z)
fun handlerMULLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithMul(left, right) } (x, y, z)
fun handlerDIVLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithDiv(left, right) } (x, y, z)
fun handlerREMLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.mkIntMod(left, right) } (x, y, z)
@Suppress("unused_parameter")
fun handlerPOWLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject, z: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? = null  // TODO
    //createBinaryIntOp { ctx, left, right ->
    //    if (right is KIntNumExpr) ctx.mkArithPower(left, right) else null
    //} (x, y, z)