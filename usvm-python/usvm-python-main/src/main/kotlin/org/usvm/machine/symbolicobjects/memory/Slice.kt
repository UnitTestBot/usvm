package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.PropertyOfPythonObject
import org.usvm.machine.symbolicobjects.SliceContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.PythonTypeSystem

data class SliceInterpretedContent(
    val start: KInterpretedValue<KIntSort>?,
    val stop: KInterpretedValue<KIntSort>?,
    val step: KInterpretedValue<KIntSort>?,
)

fun InterpretedInputSymbolicPythonObject.getSliceContent(
    ctx: PyContext,
    typeSystem: PythonTypeSystem,
): SliceInterpretedContent {
    require(getConcreteType() == typeSystem.pythonSlice)
    val startIsNone = modelHolder.model.readField(address, SliceContents.startIsNone, ctx.boolSort).isTrue
    val start = if (startIsNone) {
        null
    } else {
        modelHolder.model.readField(
            address,
            SliceContents.start,
            ctx.intSort
        ) as KInterpretedValue<KIntSort>
    }
    val stopIsNone = modelHolder.model.readField(address, SliceContents.stopIsNone, ctx.boolSort).isTrue
    val stop = if (stopIsNone) {
        null
    } else {
        modelHolder.model.readField(
            address,
            SliceContents.stop,
            ctx.intSort
        ) as KInterpretedValue<KIntSort>
    }
    val stepIsNone = modelHolder.model.readField(address, SliceContents.stepIsNone, ctx.boolSort).isTrue
    val step = if (stepIsNone) {
        null
    } else {
        modelHolder.model.readField(
            address,
            SliceContents.step,
            ctx.intSort
        ) as KInterpretedValue<KIntSort>
    }
    return SliceInterpretedContent(start, stop, step)
}

data class SliceUninterpretedField(
    val isNone: UBoolExpr,
    val content: UExpr<KIntSort>,
)

private fun UninterpretedSymbolicPythonObject.getSliceField(
    ctx: ConcolicRunContext,
    fieldIsNone: PropertyOfPythonObject,
    field: PropertyOfPythonObject,
): SliceUninterpretedField {
    val curState = ctx.curState
    requireNotNull(curState)
    addSupertype(ctx, ctx.typeSystem.pythonSlice)
    val isNone = curState.memory.readField(address, fieldIsNone, ctx.ctx.boolSort)
    val value = curState.memory.readField(address, field, ctx.ctx.intSort)
    return SliceUninterpretedField(isNone, value)
}

private fun UninterpretedSymbolicPythonObject.setSliceField(
    ctx: ConcolicRunContext,
    fieldIsNone: PropertyOfPythonObject,
    field: PropertyOfPythonObject,
    content: SliceUninterpretedField,
) {
    requireNotNull(ctx.curState)
    addSupertypeSoft(ctx, ctx.typeSystem.pythonSlice)
    ctx.extractCurState().memory.writeField(address, fieldIsNone, ctx.ctx.boolSort, content.isNone, ctx.ctx.trueExpr)
    ctx.extractCurState().memory.writeField(address, field, ctx.ctx.intSort, content.content, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.getSliceStart(ctx: ConcolicRunContext): SliceUninterpretedField =
    getSliceField(ctx, SliceContents.startIsNone, SliceContents.start)

fun UninterpretedSymbolicPythonObject.setSliceStart(ctx: ConcolicRunContext, content: SliceUninterpretedField) =
    setSliceField(ctx, SliceContents.startIsNone, SliceContents.start, content)

fun UninterpretedSymbolicPythonObject.getSliceStop(ctx: ConcolicRunContext): SliceUninterpretedField =
    getSliceField(ctx, SliceContents.stopIsNone, SliceContents.stop)

fun UninterpretedSymbolicPythonObject.setSliceStop(ctx: ConcolicRunContext, content: SliceUninterpretedField) =
    setSliceField(ctx, SliceContents.stopIsNone, SliceContents.stop, content)

fun UninterpretedSymbolicPythonObject.getSliceStep(ctx: ConcolicRunContext): SliceUninterpretedField =
    getSliceField(ctx, SliceContents.stepIsNone, SliceContents.step)

fun UninterpretedSymbolicPythonObject.setSliceStep(ctx: ConcolicRunContext, content: SliceUninterpretedField) =
    setSliceField(ctx, SliceContents.stepIsNone, SliceContents.step, content)
