package org.usvm.machine.interpreters.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.types.*

fun nbBoolKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertype(context, HasNbBool)
}

fun nbIntKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertype(context, HasNbInt)
}

fun nbAddKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIs(context, HasNbAdd) or right.evalIs(context, HasNbAdd))
}

fun nbSubtractKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIs(context, HasNbSubtract))
}

fun nbMultiplyKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject, right: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIs(context, HasNbMultiply) or right.evalIs(context, HasNbMultiply))
}

fun nbMatrixMultiplyKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) = with(context.ctx) {
    context.curState ?: return
    myAssert(context, left.evalIs(context, HasNbMatrixMultiply))
}

fun sqLengthKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertype(context, HasSqLength)
}

fun mpSubscriptKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertype(context, HasMpSubscript)
}

fun mpAssSubscriptKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    on.addSupertype(context, HasMpAssSubscript)
}

fun tpRichcmpKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, left.evalIs(context, HasTpRichcmp))
}

fun tpIterKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    context.curState ?: return
    myAssert(context, on.evalIs(context, HasTpIter))
}