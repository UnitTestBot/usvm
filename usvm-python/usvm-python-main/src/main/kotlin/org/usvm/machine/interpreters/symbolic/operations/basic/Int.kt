package org.usvm.machine.interpreters.symbolic.operations.basic

import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructFloat
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.memory.getToIntContent
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedFloatWithValue

private fun <ResSort : KSort> extractValue(
    ctx: ConcolicRunContext,
    expr: UExpr<ResSort>,
): UninterpretedSymbolicPythonObject = with(ctx.ctx) {
    @Suppress("unchecked_cast")
    when (expr.sort) {
        intSort -> constructInt(ctx, expr as UExpr<KIntSort>)
        boolSort -> constructBool(ctx, expr as UBoolExpr)
        realSort -> constructFloat(ctx, mkUninterpretedFloatWithValue(ctx.ctx, expr as UExpr<KRealSort>))
        else -> error("Bad return sort of int operation: ${expr.sort}")
    }
}

private fun <ResSort : KSort> createBinaryIntOp(
    op: (ConcolicRunContext, UExpr<KIntSort>, UExpr<KIntSort>) -> UExpr<ResSort>?,
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
                pyFork(ctx, left.evalIs(ctx, typeSystem.pythonInt))
            } else {
                pyAssert(ctx, left.evalIs(ctx, typeSystem.pythonInt))
            }
            if (right.getTypeIfDefined(ctx) != typeSystem.pythonInt) {
                pyFork(ctx, right.evalIs(ctx, typeSystem.pythonInt))
            } else {
                pyAssert(ctx, right.evalIs(ctx, typeSystem.pythonInt))
            }
            op(
                ctx,
                left.getToIntContent(ctx) ?: return@with null,
                right.getToIntContent(ctx) ?: return@with null
            )?.let { extractValue(ctx, it) }
        }
    }
}

private fun <ResSort : KSort> createUnaryIntOp(
    op: (ConcolicRunContext, UExpr<KIntSort>) -> UExpr<ResSort>?,
): (ConcolicRunContext, UninterpretedSymbolicPythonObject) -> UninterpretedSymbolicPythonObject? = { ctx, on ->
    if (ctx.curState == null) {
        null
    } else {
        with(ctx.ctx) {
            val typeSystem = ctx.typeSystem
            if (on.getTypeIfDefined(ctx) != typeSystem.pythonInt) {
                pyFork(ctx, on.evalIs(ctx, typeSystem.pythonInt))
            } else {
                pyAssert(ctx, on.evalIs(ctx, typeSystem.pythonInt))
            }
            op(
                ctx,
                on.getToIntContent(ctx) ?: return@with null
            )?.let { extractValue(ctx, it) }
        }
    }
}

fun handlerGTLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left gt right } }(context, lhs, rhs)

fun handlerLTLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left lt right } }(context, lhs, rhs)

fun handlerEQLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left eq right } }(context, lhs, rhs)

fun handlerNELongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left neq right } }(context, lhs, rhs)

fun handlerGELongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left ge right } }(context, lhs, rhs)

fun handlerLELongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> with(ctx.ctx) { left le right } }(context, lhs, rhs)

fun handlerADDLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithAdd(left, right) }(context, lhs, rhs)

fun handlerSUBLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithSub(left, right) }(context, lhs, rhs)

fun handlerMULLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkArithMul(left, right) }(context, lhs, rhs)

fun handlerDIVLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right ->
        with(ctx.ctx) {
            pyFork(ctx, right eq mkIntNum(0))
            if (ctx.modelHolder.model.eval(right eq mkIntNum(0)).isTrue) {
                null
            } else {
                mkArithDiv(left, right)
            }
        }
    }(context, lhs, rhs)

fun handlerNEGLongKt(
    context: ConcolicRunContext,
    x: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createUnaryIntOp { ctx, on -> ctx.ctx.mkArithUnaryMinus(on) }(context, x)

fun handlerPOSLongKt(
    context: ConcolicRunContext,
    x: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createUnaryIntOp { _, on -> on }(context, x)

fun handlerREMLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right -> ctx.ctx.mkIntMod(left, right) }(context, lhs, rhs)

fun handlerTrueDivLongKt(
    context: ConcolicRunContext,
    lhs: UninterpretedSymbolicPythonObject,
    rhs: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    createBinaryIntOp { ctx, left, right ->
        with(ctx.ctx) {
            pyFork(ctx, right eq mkIntNum(0))
            if (ctx.modelHolder.model.eval(right eq mkIntNum(0)).isTrue) {
                null
            } else {
                mkArithDiv(mkIntToReal(left), mkIntToReal(right))
            }
        }
    }(context, lhs, rhs)

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
