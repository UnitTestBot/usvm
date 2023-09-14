package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.language.*
import org.usvm.language.types.ArrayType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.machine.interpreters.operations.myAssert


/** int **/

fun UninterpretedSymbolicPythonObject.setIntContent(ctx: ConcolicRunContext, expr: UExpr<KIntSort>) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonInt)
    ctx.curState!!.memory.writeField(address, IntContents.content, ctx.ctx.intSort, expr, ctx.ctx.trueExpr)
}

fun UninterpretedSymbolicPythonObject.getIntContent(ctx: ConcolicRunContext): UExpr<KIntSort> {
    require(ctx.curState != null)
    addSupertype(ctx, typeSystem.pythonInt)
    return ctx.curState!!.memory.readField(address, IntContents.content, ctx.ctx.intSort)
}

fun UninterpretedSymbolicPythonObject.getToIntContent(ctx: ConcolicRunContext): UExpr<KIntSort>? = with(ctx.ctx) {
    return when (getTypeIfDefined(ctx)) {
        typeSystem.pythonInt -> getIntContent(ctx)
        typeSystem.pythonBool -> mkIte(getBoolContent(ctx), mkIntNum(1), mkIntNum(0))
        else -> null
    }
}

fun InterpretedInputSymbolicPythonObject.getIntContent(ctx: UPythonContext): KInterpretedValue<KIntSort> {
    require(getConcreteType() == typeSystem.pythonInt)
    return modelHolder.model.readField(address, IntContents.content, ctx.intSort)
}

fun InterpretedSymbolicPythonObject.getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getIntContent(ctx.ctx)
        }
        is InterpretedAllocatedSymbolicPythonObject -> {
            require(ctx.curState != null)
            ctx.curState!!.memory.readField(address, IntContents.content, ctx.ctx.intSort) as KInterpretedValue<KIntSort>
        }
    }
}


/** bool **/

fun UninterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
    require(ctx.curState != null)
    addSupertype(ctx, typeSystem.pythonBool)
    return ctx.curState!!.memory.readField(address, BoolContents.content, ctx.ctx.boolSort)
}

fun UninterpretedSymbolicPythonObject.getToBoolValue(ctx: ConcolicRunContext): UBoolExpr? = with (ctx.ctx) {
    require(ctx.curState != null)
    return when (getTypeIfDefined(ctx)) {
        typeSystem.pythonBool -> getBoolContent(ctx)
        typeSystem.pythonInt -> getIntContent(ctx) neq mkIntNum(0)
        typeSystem.pythonList -> ctx.curState!!.memory.readArrayLength(address, ArrayType) gt mkIntNum(0)
        else -> null
    }
}

fun InterpretedInputSymbolicPythonObject.getBoolContent(ctx: UPythonContext): KInterpretedValue<KBoolSort> {
    require(getConcreteType() == typeSystem.pythonBool)
    return modelHolder.model.readField(address, BoolContents.content, ctx.boolSort)
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getBoolContent(ctx.ctx)
        }
        is InterpretedAllocatedSymbolicPythonObject -> {
            require(ctx.curState != null)
            ctx.curState!!.memory.readField(address, BoolContents.content, ctx.ctx.boolSort) as KInterpretedValue<KBoolSort>
        }
    }
}


/** list_iterator **/

fun UninterpretedSymbolicPythonObject.setListIteratorContent(
    ctx: ConcolicRunContext,
    list: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonListIteratorType)
    ctx.curState!!.memory.writeField(address, ListIteratorContents.list, addressSort, list.address, trueExpr)
    ctx.curState!!.memory.writeField(address, ListIteratorContents.index, intSort, mkIntNum(0), trueExpr)
}

fun UninterpretedSymbolicPythonObject.increaseListIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonListIteratorType)
    val oldIndexValue = ctx.curState!!.memory.readField(address, ListIteratorContents.index, intSort)
    ctx.curState!!.memory.writeField(
        address,
        ListIteratorContents.index,
        intSort,
        mkArithAdd(oldIndexValue, mkIntNum(1)),
        trueExpr
    )
}

fun UninterpretedSymbolicPythonObject.getListIteratorContent(
    ctx: ConcolicRunContext
): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertype(ctx, typeSystem.pythonListIteratorType)
    val listRef = ctx.curState!!.memory.readField(address, ListIteratorContents.list, addressSort)
    val index = ctx.curState!!.memory.readField(address, ListIteratorContents.index, intSort)
    return listRef to index
}


/** tuple_iterator **/

fun UninterpretedSymbolicPythonObject.setTupleIteratorContent(ctx: ConcolicRunContext, tuple: UninterpretedSymbolicPythonObject) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    ctx.curState!!.memory.writeField(address, TupleIteratorContents.tuple, addressSort, tuple.address, trueExpr)
    ctx.curState!!.memory.writeField(address, TupleIteratorContents.index, intSort, mkIntNum(0), trueExpr)
}

fun UninterpretedSymbolicPythonObject.getTupleIteratorContent(ctx: ConcolicRunContext): Pair<UHeapRef, UExpr<KIntSort>> = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    val tupleRef = ctx.curState!!.memory.readField(address, TupleIteratorContents.tuple, addressSort)
    val index = ctx.curState!!.memory.readField(address, TupleIteratorContents.index, intSort)
    return tupleRef to index
}

fun UninterpretedSymbolicPythonObject.increaseTupleIteratorCounter(ctx: ConcolicRunContext) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonTupleIteratorType)
    val oldIndexValue = ctx.curState!!.memory.readField(address, TupleIteratorContents.index, intSort)
    ctx.curState!!.memory.writeField(
        address,
        TupleIteratorContents.index,
        intSort,
        mkArithAdd(oldIndexValue, mkIntNum(1)),
        trueExpr
    )
}


/** range **/

fun UninterpretedSymbolicPythonObject.setRangeContent(
    ctx: ConcolicRunContext,
    start: UExpr<KIntSort>,
    stop: UExpr<KIntSort>,
    step: UExpr<KIntSort>
) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonRange)
    ctx.curState!!.memory.writeField(address, RangeContents.start, intSort, start, trueExpr)
    ctx.curState!!.memory.writeField(address, RangeContents.stop, intSort, stop, trueExpr)
    ctx.curState!!.memory.writeField(address, RangeContents.step, intSort, step, trueExpr)
    val lengthRValue = mkIte(
        step gt mkIntNum(0),
        mkIte(
            stop gt start,
            mkArithDiv(
                mkArithAdd(stop, mkArithUnaryMinus(start), step, mkIntNum(-1)),
                step
            ),
            mkIntNum(0)
        ),
        mkIte(
            start gt stop,
            mkArithDiv(
                mkArithAdd(start, mkArithUnaryMinus(stop), mkArithUnaryMinus(step), mkIntNum(-1)),
                mkArithUnaryMinus(step)
            ),
            mkIntNum(0)
        )
    )
    ctx.curState!!.memory.writeField(address, RangeContents.length, intSort, lengthRValue, trueExpr)
}


/** range_iterator **/

fun UninterpretedSymbolicPythonObject.setRangeIteratorContent(
    ctx: ConcolicRunContext,
    range: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, ctx.typeSystem.pythonRangeIterator)
    val start = ctx.curState!!.memory.readField(range.address, RangeContents.start, intSort)
    ctx.curState!!.memory.writeField(address, RangeIteratorContents.start, intSort, start, trueExpr)
    val length = ctx.curState!!.memory.readField(range.address, RangeContents.length, intSort)
    ctx.curState!!.memory.writeField(address, RangeIteratorContents.length, intSort, length, trueExpr)
    val step = ctx.curState!!.memory.readField(range.address, RangeContents.step, intSort)
    ctx.curState!!.memory.writeField(address, RangeIteratorContents.step, intSort, step, trueExpr)
    val index = mkIntNum(0)
    ctx.curState!!.memory.writeField(address, RangeIteratorContents.index, intSort, index, trueExpr)
}

fun UninterpretedSymbolicPythonObject.getRangeIteratorState(
    ctx: ConcolicRunContext
): Pair<UExpr<KIntSort>, UExpr<KIntSort>> = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
    val index = ctx.curState!!.memory.readField(address, RangeIteratorContents.index, intSort)
    val length = ctx.curState!!.memory.readField(address, RangeIteratorContents.length, intSort)
    return index to length
}

fun UninterpretedSymbolicPythonObject.getRangeIteratorNext(
    ctx: ConcolicRunContext
): UExpr<KIntSort> = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertype(ctx, ctx.typeSystem.pythonRangeIterator)
    val index = ctx.curState!!.memory.readField(address, RangeIteratorContents.index, intSort)
    val newIndex = mkArithAdd(index, mkIntNum(1))
    ctx.curState!!.memory.writeField(address, RangeIteratorContents.index, intSort, newIndex, trueExpr)
    val start = ctx.curState!!.memory.readField(address, RangeIteratorContents.start, intSort)
    val step = ctx.curState!!.memory.readField(address, RangeIteratorContents.step, intSort)
    return mkArithAdd(start, mkArithMul(index, step))
}

/** slice **/

data class SliceInterpretedContent(
    val start: KInterpretedValue<KIntSort>?,
    val stop: KInterpretedValue<KIntSort>?,
    val step: KInterpretedValue<KIntSort>?
)

fun InterpretedInputSymbolicPythonObject.getSliceContent(ctx: UPythonContext, typeSystem: PythonTypeSystem): SliceInterpretedContent {
    require(getConcreteType() == typeSystem.pythonSlice)
    val startIsNone = modelHolder.model.readField(address, SliceContents.startIsNone, ctx.boolSort).isTrue
    val start = if (startIsNone) null else modelHolder.model.readField(address, SliceContents.start, ctx.intSort)
    val stopIsNone = modelHolder.model.readField(address, SliceContents.stopIsNone, ctx.boolSort).isTrue
    val stop = if (stopIsNone) null else modelHolder.model.readField(address, SliceContents.stop, ctx.intSort)
    val stepIsNone = modelHolder.model.readField(address, SliceContents.stepIsNone, ctx.boolSort).isTrue
    val step = if (stepIsNone) null else modelHolder.model.readField(address, SliceContents.step, ctx.intSort)
    return SliceInterpretedContent(start, stop, step)
}

data class SliceUninterpretedField(
    val isNone: UBoolExpr,
    val content: UExpr<KIntSort>
)

private fun UninterpretedSymbolicPythonObject.getSliceField(
    ctx: ConcolicRunContext,
    fieldIsNone: PropertyOfPythonObject,
    field: PropertyOfPythonObject
): SliceUninterpretedField {
    require(ctx.curState != null)
    addSupertype(ctx, ctx.typeSystem.pythonSlice)
    val isNone = ctx.curState!!.memory.readField(address, fieldIsNone, ctx.ctx.boolSort)
    val value = ctx.curState!!.memory.readField(address, field, ctx.ctx.intSort)
    return SliceUninterpretedField(isNone, value)
}

private fun UninterpretedSymbolicPythonObject.setSliceField(
    ctx: ConcolicRunContext,
    fieldIsNone: PropertyOfPythonObject,
    field: PropertyOfPythonObject,
    content: SliceUninterpretedField
) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, ctx.typeSystem.pythonSlice)
    ctx.curState!!.memory.writeField(address, fieldIsNone, ctx.ctx.boolSort, content.isNone, ctx.ctx.trueExpr)
    ctx.curState!!.memory.writeField(address, field, ctx.ctx.intSort, content.content, ctx.ctx.trueExpr)
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


/** str **/

fun UninterpretedSymbolicPythonObject.getConcreteStrIfDefined(preallocatedObjects: PreallocatedObjects): String? =
    preallocatedObjects.concreteString(this)