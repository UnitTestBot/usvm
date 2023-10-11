package org.usvm.machine.interpreters.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.types.*

fun nbBoolKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasNbBool)
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

fun nbMatrixMultiplyKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIsSoft(context, HasNbMatrixMultiply))
}

fun sqLengthKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasSqLength)
}

fun mpSubscriptKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasMpSubscript)
}

fun mpAssSubscriptKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertypeSoft(context, HasMpAssSubscript)
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

fun tpIterKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIsSoft(context, HasTpIter))
}