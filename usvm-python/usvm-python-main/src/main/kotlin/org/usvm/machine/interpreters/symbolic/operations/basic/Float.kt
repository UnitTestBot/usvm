package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructBool
import org.usvm.machine.symbolicobjects.constructFloat
import org.usvm.machine.symbolicobjects.constructInt
import org.usvm.machine.symbolicobjects.memory.FloatUninterpretedContent
import org.usvm.machine.symbolicobjects.memory.getFloatContent
import org.usvm.machine.symbolicobjects.memory.getToFloatContent
import org.usvm.machine.symbolicobjects.memory.getToIntContent
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedFloatWithValue
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedMinusInfinity
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedNan
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedPlusInfinity
import org.usvm.machine.symbolicobjects.memory.mkUninterpretedSignedInfinity

private fun gtFloat(
    ctx: ConcolicRunContext,
    left: FloatUninterpretedContent,
    right: FloatUninterpretedContent,
): UBoolExpr = with(
    ctx.ctx
) {
    mkIte(
        right.isNan or left.isNan,
        falseExpr,
        mkIte(
            left.isInf,
            mkIte(
                right.isInf,
                left.infSign and right.infSign.not(), // (left is inf) && (right is inf)
                left.infSign // (left is inf) && !(right is inf)
            ),
            mkIte(
                right.isInf,
                right.infSign.not(), // !(left is inf) && (right is inf)
                left.realValue gt right.realValue // !(left is inf) && !(right is inf)
            )
        )
    )
}

private fun eqFloat(
    ctx: ConcolicRunContext,
    left: FloatUninterpretedContent,
    right: FloatUninterpretedContent,
): UBoolExpr = with(
    ctx.ctx
) {
    mkIte(
        right.isNan or left.isNan,
        falseExpr,
        mkIte(
            right.isInf or left.isInf,
            (right.isInf eq left.isInf) and (right.infSign eq left.infSign),
            left.realValue eq right.realValue
        )
    )
}

fun handlerGTFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    val boolExpr: UBoolExpr = gtFloat(ctx, left, right)
    return constructBool(ctx, boolExpr)
}

fun handlerLTFloatKt(
    ctx: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? =
    handlerGTFloatKt(ctx, right, left)

fun handlerEQFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    return constructBool(ctx, eqFloat(ctx, left, right))
}


fun handlerNEFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    return constructBool(ctx, ctx.ctx.mkNot(eqFloat(ctx, left, right)))
}


fun handlerGEFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    return constructBool(ctx, ctx.ctx.mkOr(eqFloat(ctx, left, right), gtFloat(ctx, left, right)))
}

fun handlerLEFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
) =
    handlerGEFloatKt(ctx, rightObj, leftObj)


private fun <Sort : USort> constructAddExprComponent(
    ctx: ConcolicRunContext,
    left: FloatUninterpretedContent,
    right: FloatUninterpretedContent,
    projection: (FloatUninterpretedContent) -> UExpr<Sort>,
): UExpr<Sort> =
    with(ctx.ctx) {
        mkIte(
            left.isNan or right.isNan,
            projection(mkUninterpretedNan(this)),
            mkIte(
                left.isInf,
                mkIte(
                    right.isInf,
                    mkIte(
                        left.infSign neq right.infSign,
                        projection(mkUninterpretedNan(this)),
                        projection(mkUninterpretedSignedInfinity(this, left.infSign))
                    ),
                    projection(left)
                ),
                mkIte(
                    right.isInf,
                    projection(right),
                    projection(mkUninterpretedFloatWithValue(this, mkArithAdd(left.realValue, right.realValue)))
                )
            )
        )
    }

private fun constructAddExpr(
    ctx: ConcolicRunContext,
    left: FloatUninterpretedContent,
    right: FloatUninterpretedContent,
): FloatUninterpretedContent =
    FloatUninterpretedContent(
        constructAddExprComponent(ctx, left, right) { it.isNan },
        constructAddExprComponent(ctx, left, right) { it.isInf },
        constructAddExprComponent(ctx, left, right) { it.infSign },
        constructAddExprComponent(ctx, left, right) { it.realValue }
    )

private fun <Sort : USort> constructNegExprComponent(
    ctx: ConcolicRunContext,
    value: FloatUninterpretedContent,
    projection: (FloatUninterpretedContent) -> UExpr<Sort>,
): UExpr<Sort> =
    with(ctx.ctx) {
        mkIte(
            value.isNan,
            projection(mkUninterpretedNan(this)),
            mkIte(
                value.isInf,
                projection(mkUninterpretedSignedInfinity(this, value.infSign.not())),
                projection(mkUninterpretedFloatWithValue(this, mkArithUnaryMinus(value.realValue)))
            )
        )
    }

private fun constructNegExpr(
    ctx: ConcolicRunContext,
    value: FloatUninterpretedContent,
): FloatUninterpretedContent =
    FloatUninterpretedContent(
        constructNegExprComponent(ctx, value) { it.isNan },
        constructNegExprComponent(ctx, value) { it.isInf },
        constructNegExprComponent(ctx, value) { it.infSign },
        constructNegExprComponent(ctx, value) { it.realValue }
    )

private fun <Sort : USort> constructMulExprComponent(
    ctx: ConcolicRunContext,
    left: FloatUninterpretedContent,
    right: FloatUninterpretedContent,
    projection: (FloatUninterpretedContent) -> UExpr<Sort>,
): UExpr<Sort> =
    with(ctx.ctx) {
        mkIte(
            left.isNan or right.isNan,
            projection(mkUninterpretedNan(this)),
            mkIte(
                left.isInf,
                mkIte(
                    right.isInf,
                    projection(mkUninterpretedSignedInfinity(this, (left.infSign.not() xor right.infSign.not()).not())),
                    mkIte(
                        right.realValue eq mkRealNum(0),
                        projection(mkUninterpretedNan(this)),
                        projection(
                            mkUninterpretedSignedInfinity(
                                this,
                                (left.infSign.not() xor (right.realValue lt mkRealNum(0))).not()
                            )
                        )
                    )
                ),
                mkIte(
                    right.isInf,
                    mkIte(
                        left.realValue eq mkRealNum(0),
                        projection(mkUninterpretedNan(this)),
                        projection(
                            mkUninterpretedSignedInfinity(
                                this,
                                (right.infSign.not() xor (left.realValue lt mkRealNum(0))).not()
                            )
                        )
                    ),
                    projection(mkUninterpretedFloatWithValue(this, mkArithMul(left.realValue, right.realValue)))
                )
            )
        )
    }

private fun constructMulExpr(
    ctx: ConcolicRunContext,
    left: FloatUninterpretedContent,
    right: FloatUninterpretedContent,
): FloatUninterpretedContent =
    FloatUninterpretedContent(
        constructMulExprComponent(ctx, left, right) { it.isNan },
        constructMulExprComponent(ctx, left, right) { it.isInf },
        constructMulExprComponent(ctx, left, right) { it.infSign },
        constructMulExprComponent(ctx, left, right) { it.realValue }
    )

private fun <Sort : USort> constructReverseExprComponent(
    ctx: ConcolicRunContext,
    value: FloatUninterpretedContent,
    projection: (FloatUninterpretedContent) -> UExpr<Sort>,
): UExpr<Sort> =
    with(ctx.ctx) {
        mkIte(
            value.isNan,
            projection(mkUninterpretedNan(this)),
            mkIte(
                value.isInf,
                projection(mkUninterpretedFloatWithValue(this, mkRealNum(0))),
                projection(mkUninterpretedFloatWithValue(this, mkArithDiv(mkRealNum(1), value.realValue)))
            )
        )
    }

private fun constructReverseExpr(
    ctx: ConcolicRunContext,
    value: FloatUninterpretedContent,
): FloatUninterpretedContent =
    FloatUninterpretedContent(
        constructReverseExprComponent(ctx, value) { it.isNan },
        constructReverseExprComponent(ctx, value) { it.isInf },
        constructReverseExprComponent(ctx, value) { it.infSign },
        constructReverseExprComponent(ctx, value) { it.realValue }
    )

fun handlerADDFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    val floatValue = constructAddExpr(ctx, left, right)
    return constructFloat(ctx, floatValue)
}


fun handlerSUBFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    val floatValue = constructAddExpr(ctx, left, constructNegExpr(ctx, right))
    return constructFloat(ctx, floatValue)
}

fun handlerMULFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    val floatValue = constructMulExpr(ctx, left, right)
    return constructFloat(ctx, floatValue)
}

fun handlerDIVFloatKt(
    ctx: ConcolicRunContext,
    leftObj: UninterpretedSymbolicPythonObject,
    rightObj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val left = leftObj.getToFloatContent(ctx) ?: return null
    val right = rightObj.getToFloatContent(ctx) ?: return null
    pyFork(ctx, eqFloat(ctx, right, mkUninterpretedFloatWithValue(ctx.ctx, 0.0)))
    val floatValue = constructMulExpr(ctx, left, constructReverseExpr(ctx, right))
    return constructFloat(ctx, floatValue)
}

fun handlerNEGFloatKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val value = obj.getToFloatContent(ctx) ?: return null
    val result = constructNegExpr(ctx, value)
    return constructFloat(ctx, result)
}

fun handlerPOSFloatKt(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val value = obj.getToFloatContent(ctx) ?: return null
    return constructFloat(ctx, value)
}

fun castFloatToInt(
    ctx: ConcolicRunContext,
    float: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    require(float.getTypeIfDefined(ctx) == ctx.typeSystem.pythonFloat)
    val value = float.getFloatContent(ctx)
    pyFork(ctx, ctx.ctx.mkOr(value.isNan, value.isInf))
    if (ctx.modelHolder.model.eval(value.isNan).isTrue || ctx.modelHolder.model.eval(value.isInf).isTrue) {
        return null
    }
    val intValue = ctx.ctx.mkRealToInt(value.realValue)
    return constructInt(ctx, intValue)
}

private fun strToFloat(
    ctx: ConcolicRunContext,
    obj: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    require(ctx.curState != null && obj.getTypeIfDefined(ctx) == ctx.typeSystem.pythonStr)
    val str = ctx.extractCurState().preAllocatedObjects.concreteString(obj)?.lowercase() ?: return null
    if (str == "inf" || str == "infinity") {
        return constructFloat(ctx, mkUninterpretedPlusInfinity(ctx.ctx))
    }
    if (str == "-inf" || str == "-infinity") {
        return constructFloat(ctx, mkUninterpretedMinusInfinity(ctx.ctx))
    }
    return null
}

fun handlerFloatCastKt(
    ctx: ConcolicRunContext,
    arg: UninterpretedSymbolicPythonObject,
): UninterpretedSymbolicPythonObject? {
    if (ctx.curState == null) {
        return null
    }
    val typeSystem = ctx.typeSystem
    val type = arg.getTypeIfDefined(ctx) ?: return null
    return when (type) {
        typeSystem.pythonBool, typeSystem.pythonInt -> {
            val realValue = ctx.ctx.intToFloat(
                arg.getToIntContent(ctx) ?: error("bool and int should be able to be cast to int")
            )
            constructFloat(ctx, mkUninterpretedFloatWithValue(ctx.ctx, realValue))
        }
        typeSystem.pythonFloat -> {
            arg
        }
        typeSystem.pythonStr -> {
            strToFloat(ctx, arg)
        }
        else -> {
            null
        }
    }
}
