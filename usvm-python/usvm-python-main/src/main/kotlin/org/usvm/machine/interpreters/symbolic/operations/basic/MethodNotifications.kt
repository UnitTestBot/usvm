package org.usvm.machine.interpreters.symbolic.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.types.*

@Suppress("unused_parameter")
fun nbBoolKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    // on.addSupertypeSoft(context, HasNbBool)  // TODO
}

fun nbIntKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasNbInt)
}

fun nbAddKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIsSoft(context, HasNbAdd) or right.evalIsSoft(context, HasNbAdd))
}

fun nbSubtractKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIsSoft(context, HasNbSubtract))
}

fun nbMultiplyKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIsSoft(context, HasNbMultiply) or right.evalIsSoft(context, HasNbMultiply))
}

fun nbMatrixMultiplyKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, left.evalIsSoft(context, HasNbMatrixMultiply))
}

fun nbNegativeKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasNbNegative))
}

fun nbPositiveKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasNbPositive))
}

fun sqLengthKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    val sqLength = on.evalIsSoft(context, HasSqLength)
    val mpLength = on.evalIsSoft(context, HasMpLength)
    myAssert(context, context.ctx.mkOr(sqLength, mpLength))
}

fun mpSubscriptKt(
    context: ConcolicRunContext,
    on: UninterpretedSymbolicPythonObject,
    index: UninterpretedSymbolicPythonObject
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
    index: UninterpretedSymbolicPythonObject
) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasMpAssSubscript)
    if (index.getTypeIfDefined(context) == null && on.getTypeIfDefined(context) != context.typeSystem.pythonDict) {
        index.addSupertype(context, HasNbIndex)
    }
}

fun tpRichcmpKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, left.evalIsSoft(context, HasTpRichcmp))
}

fun tpGetattroKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject, name: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasTpGetattro))
    myAssert(context, name.evalIsSoft(context, context.typeSystem.pythonStr))
}

fun tpSetattroKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject, name: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasTpSetattro))
    myAssert(context, name.evalIsSoft(context, context.typeSystem.pythonStr))
}

fun tpIterKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasTpIter))
}

fun tpCallKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasTpCall))
}

fun tpHashKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasTpHash))
}