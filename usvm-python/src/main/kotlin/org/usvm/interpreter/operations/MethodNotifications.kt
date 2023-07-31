package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.types.HasMpSubscript
import org.usvm.language.types.HasNbBool
import org.usvm.language.types.HasNbInt
import org.usvm.language.types.HasTpRichcmp

fun nbBoolKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    on.addSupertype(context, HasNbBool)
}

fun nbIntKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    on.addSupertype(context, HasNbInt)
}

fun mpSubscriptKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    on.addSupertype(context, HasMpSubscript)
}

fun tpRichcmpKt(context: ConcolicRunContext, left: UninterpretedSymbolicPythonObject) {
    myAssert(context, left.evalIs(context, HasTpRichcmp))
}