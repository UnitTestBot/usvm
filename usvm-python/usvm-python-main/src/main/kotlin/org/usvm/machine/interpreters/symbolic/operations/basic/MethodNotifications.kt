package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.getFieldValue
import org.usvm.machine.types.HasMpAssSubscript
import org.usvm.machine.types.HasMpLength
import org.usvm.machine.types.HasMpSubscript
import org.usvm.machine.types.HasNbAdd
import org.usvm.machine.types.HasNbIndex
import org.usvm.machine.types.HasNbInt
import org.usvm.machine.types.HasNbMatrixMultiply
import org.usvm.machine.types.HasNbMultiply
import org.usvm.machine.types.HasNbNegative
import org.usvm.machine.types.HasNbPositive
import org.usvm.machine.types.HasNbSubtract
import org.usvm.machine.types.HasSqConcat
import org.usvm.machine.types.HasSqLength
import org.usvm.machine.types.HasTpCall
import org.usvm.machine.types.HasTpGetattro
import org.usvm.machine.types.HasTpHash
import org.usvm.machine.types.HasTpIter
import org.usvm.machine.types.HasTpRichcmp
import org.usvm.machine.types.HasTpSetattro
import org.usvm.machine.types.MockType

fun nbIntKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasNbInt)
}

fun nbAddKt(
    context: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
) = with(
    context.ctx
) {
    context.curState ?: return
    /*
     * The __add__ method corresponds both to the nb_add and sq_concat slots,
     * so it is crucial not to assert the presence of nb_add, but to fork on these
     * two possible options.
     * Moreover, for now it was decided, that operation `sq_concat` makes sense
     * only in the situation, when both operands have the corresponding slot.
     */
    val nbAdd = left.evalIsSoft(context, HasNbAdd) or right.evalIsSoft(context, HasNbAdd)
    val sqConcat = left.evalIsSoft(context, HasSqConcat) and right.evalIsSoft(context, HasSqConcat)
    pyAssert(context, nbAdd.not() implies sqConcat)
    pyFork(context, nbAdd)
}

fun nbSubtractKt(
    context: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
) = with(context.ctx) {
    context.curState ?: return
    pyAssert(context, left.evalIsSoft(context, HasNbSubtract))
}

fun nbMultiplyKt(
    context: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
) = with(
    context.ctx
) {
    context.curState ?: return
    pyAssert(context, left.evalIsSoft(context, HasNbMultiply) or right.evalIsSoft(context, HasNbMultiply))
}

fun nbMatrixMultiplyKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, left.evalIsSoft(context, HasNbMatrixMultiply))
}

fun nbNegativeKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, on.evalIsSoft(context, HasNbNegative))
}

fun nbPositiveKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, on.evalIsSoft(context, HasNbPositive))
}

fun sqConcatKt(
    context: ConcolicRunContext,
    left: UninterpretedSymbolicPythonObject,
    right: UninterpretedSymbolicPythonObject,
) = with(
    context.ctx
) {
    context.curState ?: return
    pyAssert(context, left.evalIsSoft(context, HasSqConcat) and right.evalIsSoft(context, HasSqConcat))
}

fun sqLengthKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    val sqLength = on.evalIsSoft(context, HasSqLength)
    val mpLength = on.evalIsSoft(context, HasMpLength)
    pyAssert(context, context.ctx.mkOr(sqLength, mpLength))
}

fun mpSubscriptKt(
    context: ConcolicRunContext,
    on: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasMpSubscript)
    if (index.getTypeIfDefined(context) == null && on.getTypeIfDefined(context) != context.typeSystem.pythonDict) {
        index.addSupertype(context, HasNbIndex)
    }
}

fun mpAssSubscriptKt(
    context: ConcolicRunContext,
    on: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject,
) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasMpAssSubscript)
    if (index.getTypeIfDefined(context) == null && on.getTypeIfDefined(context) != context.typeSystem.pythonDict) {
        index.addSupertype(context, HasNbIndex)
    }
}

fun tpRichcmpKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, left.evalIsSoft(context, HasTpRichcmp))
}

fun tpGetattroKt(
    ctx: ConcolicRunContext,
    on: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject,
) {
    ctx.curState ?: return
    pyAssert(ctx, on.evalIsSoft(ctx, HasTpGetattro))
    pyAssert(ctx, name.evalIsSoft(ctx, ctx.typeSystem.pythonStr))
    val field = on.getFieldValue(ctx, name)
    val softConstraint = field.evalIsSoft(ctx, MockType)
    val ps = ctx.extractCurState().pathConstraints
    ps.pythonSoftConstraints = ps.pythonSoftConstraints.add(softConstraint)
}

fun tpSetattroKt(
    context: ConcolicRunContext,
    on: UninterpretedSymbolicPythonObject,
    name: UninterpretedSymbolicPythonObject,
) {
    context.curState ?: return
    pyAssert(context, on.evalIsSoft(context, HasTpSetattro))
    pyAssert(context, name.evalIsSoft(context, context.typeSystem.pythonStr))
}

fun tpIterKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, on.evalIsSoft(context, HasTpIter))
}

fun tpCallKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, on.evalIsSoft(context, HasTpCall))
}

fun tpHashKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    pyAssert(context, on.evalIsSoft(context, HasTpHash))
}
