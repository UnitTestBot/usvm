package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KFp64Value
import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import org.usvm.*
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.api.typeStreamOf
import org.usvm.api.writeField
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.machine.UPythonContext
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.operations.basic.myAssert
import org.usvm.machine.utils.PyModelWrapper
import org.usvm.memory.UMemory
import org.usvm.types.first

/** standard fields **/

fun UninterpretedSymbolicPythonObject.getFieldValue(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject {
    require(ctx.curState != null)
    name.addSupertype(ctx, typeSystem.pythonStr)
    val addr = ctx.curState!!.symbolicObjectMapGet(address, name.address, ObjectDictType, ctx.ctx.addressSort)
    return UninterpretedSymbolicPythonObject(addr, typeSystem)
}

fun UninterpretedSymbolicPythonObject.setFieldValue(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    require(ctx.curState != null)
    name.addSupertypeSoft(ctx, typeSystem.pythonStr)
    ctx.curState!!.symbolicObjectMapPut(address, name.address, value.address, ObjectDictType, ctx.ctx.addressSort)
}

fun UninterpretedSymbolicPythonObject.containsField(
    ctx: ConcolicRunContext,
    name: UninterpretedSymbolicPythonObject
): UBoolExpr {
    require(ctx.curState != null)
    name.addSupertype(ctx, typeSystem.pythonStr)
    return ctx.curState!!.symbolicObjectMapContains(address, name.address, ObjectDictType)
}

fun InterpretedInputSymbolicPythonObject.containsField(
    name: InterpretedSymbolicPythonObject
): Boolean {
    require(!isAllocatedConcreteHeapRef(name.address))
    val result = modelHolder.model.uModel.read(URefSetEntryLValue(address, name.address, ObjectDictType))
    return result.isTrue
}

fun InterpretedInputSymbolicPythonObject.getFieldValue(
    ctx: UPythonContext,
    name: InterpretedSymbolicPythonObject,
    memory: UMemory<PythonType, PythonCallable>
): InterpretedSymbolicPythonObject {
    require(!isAllocatedConcreteHeapRef(name.address))
    val result = modelHolder.model.uModel.read(URefMapEntryLValue(ctx.addressSort, address, name.address, ObjectDictType))
    require((result as UConcreteHeapRef).address <= 0)
    return if (!isStaticHeapRef(result))
        InterpretedInputSymbolicPythonObject(result, modelHolder, typeSystem)
    else {
        val type = memory.typeStreamOf(result).first()
        require(type is ConcretePythonType)
        InterpretedAllocatedOrStaticSymbolicPythonObject(result, type, typeSystem)
    }
}

/** arrays (list, tuple) **/

fun UninterpretedSymbolicPythonObject.readArrayLength(ctx: ConcolicRunContext): UExpr<KIntSort> {
    val type = getTypeIfDefined(ctx)
    require(type != null && type is ArrayLikeConcretePythonType)
    val result = ctx.curState!!.memory.readArrayLength(address, ArrayType, ctx.ctx.intSort)
    myAssert(ctx, ctx.ctx.mkArithGe(result, ctx.ctx.mkIntNum(0)))
    return result
}

fun InterpretedInputSymbolicPythonObject.readArrayLength(ctx: UPythonContext): UExpr<KIntSort> {
    require(getConcreteType() != null && getConcreteType() is ArrayLikeConcretePythonType)
    return modelHolder.model.uModel.readArrayLength(address, ArrayType, ctx.intSort)
}

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

fun InterpretedSymbolicPythonObject.getIntContent(ctx: UPythonContext, memory: UMemory<PythonType, PythonCallable>): KInterpretedValue<KIntSort> {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getIntContent(ctx)
        }
        is InterpretedAllocatedOrStaticSymbolicPythonObject -> {
            memory.readField(address, IntContents.content, ctx.intSort) as KInterpretedValue<KIntSort>
        }
    }
}

fun InterpretedSymbolicPythonObject.getIntContent(ctx: ConcolicRunContext): KInterpretedValue<KIntSort> {
    require(ctx.curState != null)
    return getIntContent(ctx.ctx, ctx.curState!!.memory)
}


/** float **/

sealed class FloatInterpretedContent
object FloatNan: FloatInterpretedContent()
object FloatPlusInfinity: FloatInterpretedContent()
object FloatMinusInfinity: FloatInterpretedContent()
data class FloatNormalValue(val value: Double): FloatInterpretedContent()

private fun readBoolFieldWithSoftConstraint(field: ContentOfType, model: PyModelWrapper, address: UConcreteHeapRef, ctx: UPythonContext): UBoolExpr {
    val value = model.readField(address, field, ctx.intSort)
    return ctx.mkArithGt(value, ctx.mkIntNum(FloatContents.bound))
}

private fun readBoolFieldWithSoftConstraint(field: ContentOfType, memory: UMemory<PythonType, PythonCallable>, address: UHeapRef, ctx: UPythonContext): UBoolExpr {
    val value = memory.readField(address, field, ctx.intSort)
    return ctx.mkArithGt(value, ctx.mkIntNum(FloatContents.bound))
}

private fun writeBoolFieldWithSoftConstraint(field: ContentOfType, memory: UMemory<PythonType, PythonCallable>, address: UHeapRef, ctx: UPythonContext, value: UBoolExpr) {
    val intValue = ctx.mkIte(value, ctx.mkIntNum(FloatContents.bound + 1), ctx.mkIntNum(0))
    memory.writeField(address, field, ctx.intSort, intValue, ctx.trueExpr)
}

fun InterpretedInputSymbolicPythonObject.getFloatContent(ctx: UPythonContext): FloatInterpretedContent {
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

fun InterpretedSymbolicPythonObject.getFloatContent(ctx: UPythonContext, memory: UMemory<PythonType, PythonCallable>): FloatInterpretedContent {
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

fun mkUninterpretedNan(ctx: UPythonContext): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.trueExpr, ctx.falseExpr, ctx.falseExpr, ctx.mkRealNum(0))

fun mkUninterpretedPlusInfinity(ctx: UPythonContext): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.trueExpr, ctx.trueExpr, ctx.mkRealNum(0))

fun mkUninterpretedMinusInfinity(ctx: UPythonContext): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.trueExpr, ctx.falseExpr, ctx.mkRealNum(0))

fun mkUninterpretedSignedInfinity(ctx: UPythonContext, infSign: UBoolExpr): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.trueExpr, infSign, ctx.mkRealNum(0))

fun mkUninterpretedFloatWithValue(ctx: UPythonContext, value: Double): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.falseExpr, ctx.falseExpr, ctx.mkFpToRealExpr(ctx.mkFp64(value)))

fun mkUninterpretedFloatWithValue(ctx: UPythonContext, value: UExpr<KRealSort>): FloatUninterpretedContent =
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

private fun wrapRealValue(ctx: UPythonContext, value: UExpr<KRealSort>): FloatUninterpretedContent =
    FloatUninterpretedContent(ctx.falseExpr, ctx.falseExpr, ctx.falseExpr, value)

fun UninterpretedSymbolicPythonObject.getToFloatContent(ctx: ConcolicRunContext): FloatUninterpretedContent? = with(ctx.ctx) {
    return when (getTypeIfDefined(ctx)) {
        typeSystem.pythonFloat -> getFloatContent(ctx)
        typeSystem.pythonInt -> wrapRealValue(ctx.ctx, intToFloat(getIntContent(ctx)))
        typeSystem.pythonBool -> wrapRealValue(ctx.ctx, intToFloat(getToIntContent(ctx)!!))
        else -> null
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
    return when (val type = getTypeIfDefined(ctx)) {
        typeSystem.pythonBool -> getBoolContent(ctx)
        typeSystem.pythonInt -> getIntContent(ctx) neq mkIntNum(0)
        typeSystem.pythonList, typeSystem.pythonTuple -> readArrayLength(ctx) gt mkIntNum(0)
        typeSystem.pythonNoneType -> falseExpr
        is ConcretePythonType -> {
            val address = ctx.typeSystem.addressOfConcreteType(type)
            if (!ConcretePythonInterpreter.typeHasNbBool(address) && !ConcretePythonInterpreter.typeHasSqLength(address))
                trueExpr
            else
                null
        }
        else -> null
    }
}

fun InterpretedInputSymbolicPythonObject.getBoolContent(ctx: UPythonContext): KInterpretedValue<KBoolSort> {
    require(getConcreteType() == typeSystem.pythonBool)
    return modelHolder.model.readField(address, BoolContents.content, ctx.boolSort)
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: UPythonContext, memory: UMemory<PythonType, PythonCallable>): KInterpretedValue<KBoolSort> {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getBoolContent(ctx)
        }
        is InterpretedAllocatedOrStaticSymbolicPythonObject -> {
            memory.readField(address, BoolContents.content, ctx.boolSort) as KInterpretedValue<KBoolSort>
        }
    }
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): KInterpretedValue<KBoolSort> {
    require(ctx.curState != null)
    return getBoolContent(ctx.ctx, ctx.curState!!.memory)
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