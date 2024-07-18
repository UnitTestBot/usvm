package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KFp64Value
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isTrue
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.ContentOfType
import org.usvm.machine.symbolicobjects.FloatContents
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.PythonType
import org.usvm.memory.UMemory

sealed class FloatInterpretedContent
data object FloatNan : FloatInterpretedContent()
data object FloatPlusInfinity : FloatInterpretedContent()
data object FloatMinusInfinity : FloatInterpretedContent()
data class FloatNormalValue(val value: Double) : FloatInterpretedContent()

private fun readBoolFieldWithSoftConstraint(
    field: ContentOfType<KIntSort>,
    model: PyModel,
    address: UConcreteHeapRef,
    ctx: PyContext,
): UBoolExpr {
    val value = model.readField(address, field, field.sort(ctx))
    return ctx.mkArithGt(value, ctx.mkIntNum(FloatContents.BOUND))
}

private fun readBoolFieldWithSoftConstraint(
    field: ContentOfType<KIntSort>,
    memory: UMemory<PythonType, PyCallable>,
    address: UHeapRef,
    ctx: PyContext,
): UBoolExpr {
    val value = memory.readField(address, field, field.sort(ctx))
    return ctx.mkArithGt(value, ctx.mkIntNum(FloatContents.BOUND))
}

private fun writeBoolFieldWithSoftConstraint(
    field: ContentOfType<KIntSort>,
    memory: UMemory<PythonType, PyCallable>,
    address: UHeapRef,
    ctx: PyContext,
    value: UBoolExpr,
) {
    val intValue = ctx.mkIte(value, ctx.mkIntNum(FloatContents.BOUND + 1), ctx.mkIntNum(0))
    memory.writeField(address, field, field.sort(ctx), intValue, ctx.trueExpr)
}

fun InterpretedInputSymbolicPythonObject.getFloatContent(ctx: PyContext): FloatInterpretedContent {
    require(getConcreteType() == typeSystem.pythonFloat)
    val isNan = readBoolFieldWithSoftConstraint(FloatContents.isNan, modelHolder.model, address, ctx)
    if (isNan.isTrue) {
        return FloatNan
    }
    val isInf = readBoolFieldWithSoftConstraint(FloatContents.isInf, modelHolder.model, address, ctx)
    if (isInf.isTrue) {
        val isPositive = modelHolder.model.readField(address, FloatContents.infSign, FloatContents.infSign.sort(ctx))
        return if (isPositive.isTrue) FloatPlusInfinity else FloatMinusInfinity
    }
    val realValue = modelHolder.model.readField(address, FloatContents.content, FloatContents.content.sort(ctx))
    val floatValue = ctx.mkRealToFpExpr(ctx.fp64Sort, ctx.floatRoundingMode, realValue) as KFp64Value
    return FloatNormalValue(floatValue.value)
}

fun InterpretedSymbolicPythonObject.getFloatContent(
    ctx: PyContext,
    memory: UMemory<PythonType, PyCallable>,
): FloatInterpretedContent {
    if (this is InterpretedInputSymbolicPythonObject) {
        return getFloatContent(ctx)
    }
    val isNan = memory.readField(address, FloatContents.isNan, ctx.boolSort)
    if (isNan.isTrue) {
        return FloatNan
    }
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
    val realValue: UExpr<KRealSort>,
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
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, typeSystem.pythonFloat)
    writeBoolFieldWithSoftConstraint(FloatContents.isNan, ctx.extractCurState().memory, address, ctx.ctx, expr.isNan)
    writeBoolFieldWithSoftConstraint(FloatContents.isInf, ctx.extractCurState().memory, address, ctx.ctx, expr.isInf)
    ctx.extractCurState()
        .memory
        .writeField(address, FloatContents.infSign, ctx.ctx.boolSort, expr.infSign, ctx.ctx.trueExpr)
    ctx.extractCurState()
        .memory
        .writeField(address, FloatContents.content, ctx.ctx.realSort, expr.realValue, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.getFloatContent(ctx: ConcolicRunContext): FloatUninterpretedContent {
    requireNotNull(ctx.curState)
    addSupertype(ctx, typeSystem.pythonFloat)
    return FloatUninterpretedContent(
        readBoolFieldWithSoftConstraint(FloatContents.isNan, ctx.extractCurState().memory, address, ctx.ctx),
        readBoolFieldWithSoftConstraint(FloatContents.isInf, ctx.extractCurState().memory, address, ctx.ctx),
        ctx.extractCurState().memory.readField(address, FloatContents.infSign, ctx.ctx.boolSort),
        ctx.extractCurState().memory.readField(address, FloatContents.content, ctx.ctx.realSort)
    )
}

private fun wrapRealValue(ctx: PyContext, value: UExpr<KRealSort>): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.falseExpr, ctx.falseExpr, value)

fun UninterpretedSymbolicPythonObject.getToFloatContent(ctx: ConcolicRunContext): FloatUninterpretedContent? = with(
    ctx.ctx
) {
    return when (getTypeIfDefined(ctx)) {
        typeSystem.pythonFloat -> getFloatContent(ctx)
        typeSystem.pythonInt -> wrapRealValue(ctx.ctx, intToFloat(getIntContent(ctx)))
        typeSystem.pythonBool -> wrapRealValue(
            ctx.ctx,
            intToFloat(
                getToIntContent(ctx) ?: error("Cannot convert bool to int")
            )
        )
        else -> null
    }
}

