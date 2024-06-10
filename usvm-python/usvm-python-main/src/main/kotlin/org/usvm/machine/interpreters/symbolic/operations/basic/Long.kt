package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructFloat
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.memory.getToIntContent
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedFloatWithValue

private fun <RES_SORT : KSort> extractValue(
    ctx: ConcolicRunContext,
    expr: UExpr<RES_SORT>,
): UninterpretedSymbolicPythonObject = with(ctx.ctx) {
    @Suppress("unchecked_cast")
    when (expr.sort) {
        intSort -> constructInt(ctx, expr as UExpr<KIntSort>)
        boolSort -> constructBool(ctx, expr as UBoolExpr)
        realSort -> constructFloat(ctx, mkUninterpretedFloatWithValue(ctx.ctx, expr as UExpr<KRealSort>))
        else -> error("Bad return sort of int operation: ${expr.sort}")
    }
}

private fun <RES_SORT : KSort> createBinaryIntOp(
    op: (ConcolicRunContext, UExpr<KIntSort>, UExpr<KIntSort>) -> UExpr<RES_SORT>?,
): (
    ConcolicRunContext,
    UninterpretedSymbolicPythonObject,
    UninterpretedSymbolicPythonObject,
) -> UninterpretedSymbolicPythonObject? = { ctx, left, right ->
    if (ctx.curState == null) {
        null
    } else {
        with(ctx.ctx) {
            val typeSystem = ctx.typeSystem
            if (left.getTypeIfDefined(ctx) != typeSystem.pythonInt) {
                myFork(ctx, left.evalIs(ctx, typeSystem.pythonInt))
            } else {
                myAssert(ctx, left.evalIs(ctx, typeSystem.pythonInt))
            }
            if (right.getTypeIfDefined(ctx) != typeSystem.pythonInt) {
                myFork(ctx, right.evalIs(ctx, typeSystem.pythonInt))
            } else {
                myAssert(ctx, right.evalIs(ctx, typeSystem.pythonInt))
            }
            op(
                ctx,
                left.getToIntContent(ctx) ?: return@with null,
                right.getToIntContent(ctx) ?: return@with null
            )?.let { extractValue(ctx, it) }
        }
    }
}

private fun <RES_SORT : KSort> createUnaryIntOp(
    op: (ConcolicRunContext, UExpr<KIntSort>) -> UExpr<RES_SORT>?,
): (ConcolicRunContext, UninterpretedSymbolicPythonObject) -> UninterpretedSymbolicPythonObject? = { ctx, on ->
    if (ctx.curState == null) {
        null
    } else {
        with(ctx.ctx) {
            val typeSystem = ctx.typeSystem
            if (on.getTypeIfDefined(ctx) != typeSystem.pythonInt) {
                myFork(ctx, on.evalIs(ctx, typeSystem.pythonInt))
            } else {
                myAssert(ctx, on.evalIs(ctx, typeSystem.pythonInt))
            }
            op(
                ctx,
                on.getToIntContent(ctx) ?: return@with null
            )?.let { extractValue(ctx, it) }
        }
    }
}

fun handlerGTLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left gt right } }(x, y, z)

fun handlerLTLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left lt right } }(x, y, z)

fun handlerEQLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left eq right } }(x, y, z)

fun handlerNELongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left neq right } }(x, y, z)

fun handlerGELongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left ge right } }(x, y, z)

fun handlerLELongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left le right } }(x, y, z)

fun handlerADDLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithAdd(left, right) }(x, y, z)

fun handlerSUBLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithSub(left, right) }(x, y, z)

fun handlerMULLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithMul(left, right) }(x, y, z)

fun handlerDIVLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right ->
        with(ctx.ctx) {
            myFork(ctx, right eq mkIntNum(0))
            if (ctx.modelHolder.model.eval(right eq mkIntNum(0)).isTrue) {
                null
            } else {
                mkArithDiv(left, right)
            }
        }
    }(x, y, z)

fun handlerNEGLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createUnaryIntOp { ctx, on -> ctx.ctx.mkArithUnaryMinus(on) }(x, y)

fun handlerPOSLongKt(x: ConcolicRunContext, y: UninterpretedSymbolicPythonObject): UninterpretedSymbolicPythonObject? =
    createUnaryIntOp { _, on -> on }(x, y)

fun handlerREMLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkIntMod(left, right) }(x, y, z)

fun handlerTrueDivLongKt(
    x: ConcolicRunContext,
    y: UninterpretedSymbolicPythonObject,
    z: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right ->
        with(ctx.ctx) {
            myFork(ctx, right eq mkIntNum(0))
            if (ctx.modelHolder.model.eval(right eq mkIntNum(0)).isTrue) {
                null
            } else {
                mkArithDiv(mkIntToReal(left), mkIntToReal(right))
            }
        }
    }(x, y, z)

fun handlerIntCastKt(
    ctx: ConcolicRunContext,
    arg: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    val type = arg.getTypeIfDefined(ctx) ?: return null
    return when (type) {
        typeSystem.pythonInt -> arg
        typeSystem.pythonBool -> constructInt(
            ctx,
            arg.getToIntContent(ctx) ?: error("It should be possible to cast bool to int")
        )
        typeSystem.pythonFloat -> castFloatToInt(ctx, arg)
        else -> null
    }
}
