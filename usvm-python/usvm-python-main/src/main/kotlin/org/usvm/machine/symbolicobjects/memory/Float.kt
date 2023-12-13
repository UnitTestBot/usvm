package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KFp64Value
import io.ksmt.sort.KRealSort
import org.usvm.*
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonCallable
import org.usvm.language.types.PythonType
import org.usvm.machine.PyContext
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.*
import org.usvm.memory.UMemory


sealed class FloatInterpretedContent
object FloatNan: FloatInterpretedContent()
object FloatPlusInfinity: FloatInterpretedContent()
object FloatMinusInfinity: FloatInterpretedContent()
data class FloatNormalValue(val value: Double): FloatInterpretedContent()

private fun readBoolFieldWithSoftConstraint(field: ContentOfType, model: PyModel, address: UConcreteHeapRef, ctx: PyContext): UBoolExpr {
    val value = model.readField(address, field, ctx.intSort)
    return ctx.mkArithGt(value, ctx.mkIntNum(FloatContents.bound))
}

private fun readBoolFieldWithSoftConstraint(field: ContentOfType, memory: UMemory<PythonType, PythonCallable>, address: UHeapRef, ctx: PyContext): UBoolExpr {
    val value = memory.readField(address, field, ctx.intSort)
    return ctx.mkArithGt(value, ctx.mkIntNum(FloatContents.bound))
}

private fun writeBoolFieldWithSoftConstraint(field: ContentOfType, memory: UMemory<PythonType, PythonCallable>, address: UHeapRef, ctx: PyContext, value: UBoolExpr) {
    val intValue = ctx.mkIte(value, ctx.mkIntNum(FloatContents.bound + 1), ctx.mkIntNum(0))
    memory.writeField(address, field, ctx.intSort, intValue, ctx.trueExpr)
}

fun InterpretedInputSymbolicPythonObject.getFloatContent(ctx: PyContext): FloatInterpretedContent {
    require(getConcreteType() == typeSystem.pythonFloat)
    val isNan = readBoolFieldWithSoftConstraint(FloatContents.isNan, modelHolder.model, address, ctx)
    if (isNan.isTrue)
        return FloatNan
    val isInf = readBoolFieldWithSoftConstraint(FloatContents.isInf, modelHolder.model, address, ctx)
    if (isInf.isTrue) {
        val isPositive = modelHolder.model.readField(address, FloatContents.infSign, ctx.boolSort)
        return if (isPositive.isTrue) FloatPlusInfinity else FloatMinusInfinity
    }
    val realValue = modelHolder.model.readField(address, FloatContents.content, ctx.realSort)
    val floatValue = ctx.mkRealToFpExpr(ctx.fp64Sort, ctx.floatRoundingMode, realValue) as KFp64Value
    return FloatNormalValue(floatValue.value)
}

fun InterpretedSymbolicPythonObject.getFloatContent(ctx: PyContext, memory: UMemory<PythonType, PythonCallable>): FloatInterpretedContent {
    if (this is InterpretedInputSymbolicPythonObject)
        return getFloatContent(ctx)
    val isNan = memory.readField(address, FloatContents.isNan, ctx.boolSort)
    if (isNan.isTrue)
        return FloatNan
    val isInf = memory.readField(address, FloatContents.isInf, ctx.boolSort)
    if (isInf.isTrue) {
        val isPositive = memory.readField(address, FloatContents.infSign, ctx.boolSort)
        return if (isPositive.isTrue) FloatPlusInfinity else FloatMinusInfinity
    }
    val realValue = memory.readField(address, FloatContents.content, ctx.realSort)
    val floatValue = ctx.mkRealToFpExpr(ctx.fp64Sort, ctx.floatRoundingMode, realValue) as KFp64Value
    return FloatNormalValue(floatValue.value)
}

data class FloatUninterpretedContent(
    val isNan: UBoolExpr,
    val isInf: UBoolExpr,
    val infSign: UBoolExpr,
    val realValue: UExpr<KRealSort>
)

fun mkUninterpretedNan(ctx: PyContext): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.trueExpr, ctx.falseExpr, ctx.falseExpr, ctx.mkRealNum(0))

fun mkUninterpretedPlusInfinity(ctx: PyContext): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.trueExpr, ctx.trueExpr, ctx.mkRealNum(0))

fun mkUninterpretedMinusInfinity(ctx: PyContext): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.trueExpr, ctx.falseExpr, ctx.mkRealNum(0))

fun mkUninterpretedSignedInfinity(ctx: PyContext, infSign: UBoolExpr): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.trueExpr, infSign, ctx.mkRealNum(0))

fun mkUninterpretedFloatWithValue(ctx: PyContext, value: Double): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.falseExpr, ctx.falseExpr, ctx.mkFpToRealExpr(ctx.mkFp64(value)))

fun mkUninterpretedFloatWithValue(ctx: PyContext, value: UExpr<KRealSort>): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.falseExpr, ctx.falseExpr, value)

fun UninterpretedSymbolicPythonObject.setFloatContent(ctx: ConcolicRunContext, expr: FloatUninterpretedContent) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonFloat)
    writeBoolFieldWithSoftConstraint(FloatContents.isNan, ctx.curState!!.memory, address, ctx.ctx, expr.isNan)
    writeBoolFieldWithSoftConstraint(FloatContents.isInf, ctx.curState!!.memory, address, ctx.ctx, expr.isInf)
    ctx.curState!!.memory.writeField(address, FloatContents.infSign, ctx.ctx.boolSort, expr.infSign, ctx.ctx.trueExpr)
    ctx.curState!!.memory.writeField(address, FloatContents.content, ctx.ctx.realSort, expr.realValue, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.getFloatContent(ctx: ConcolicRunContext): FloatUninterpretedContent {
    require(ctx.curState != null)
    addSupertype(ctx, typeSystem.pythonFloat)
    return FloatUninterpretedContent(
        readBoolFieldWithSoftConstraint(FloatContents.isNan, ctx.curState!!.memory, address, ctx.ctx),
        readBoolFieldWithSoftConstraint(FloatContents.isInf, ctx.curState!!.memory, address, ctx.ctx),
        ctx.curState!!.memory.readField(address, FloatContents.infSign, ctx.ctx.boolSort),
        ctx.curState!!.memory.readField(address, FloatContents.content, ctx.ctx.realSort)
    )
}

private fun wrapRealValue(ctx: PyContext, value: UExpr<KRealSort>): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.falseExpr, ctx.falseExpr, value)

fun UninterpretedSymbolicPythonObject.getToFloatContent(ctx: ConcolicRunContext): FloatUninterpretedContent? = with(ctx.ctx) {
    return when (getTypeIfDefined(ctx)) {
        typeSystem.pythonFloat -> getFloatContent(ctx)
        typeSystem.pythonInt -> wrapRealValue(ctx.ctx, intToFloat(getIntContent(ctx)))
        typeSystem.pythonBool -> wrapRealValue(ctx.ctx, intToFloat(getToIntContent(ctx)!!))
        else -> null
    }
}

