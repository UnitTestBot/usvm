package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.language.VirtualPythonObject

fun virtualNbBoolKt(context: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    return context.curState.pyModel.eval(on.origin.obj.getBoolContent(context)).isTrue
}