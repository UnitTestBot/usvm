package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.language.types.HasNbBool

fun nbBoolKt(context: ConcolicRunContext, on: UninterpretedSymbolicPythonObject) {
    on.addSupertype(context, HasNbBool)
}