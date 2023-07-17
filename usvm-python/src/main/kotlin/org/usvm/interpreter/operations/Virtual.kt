package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.language.VirtualPythonObject

fun nbBoolKt(context: ConcolicRunContext, obj: VirtualPythonObject): Boolean {
    val symbolicValue = obj.symbol.obj.getBoolContent(context)
    return context.curState.pyModel.eval(symbolicValue).isTrue
}