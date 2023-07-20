package org.usvm.interpreter.operations

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.emptyNamespace
import org.usvm.isTrue
import org.usvm.language.VirtualPythonObject

fun virtualNbBoolKt(context: ConcolicRunContext, on: VirtualPythonObject): Boolean {
    return context.curState.pyModel.eval(on.origin.obj.getBoolContent(context)).isTrue
}

fun virtualNbIntKt(context: ConcolicRunContext, on: VirtualPythonObject): PythonObject {
    val expr = context.curState.pyModel.eval(on.origin.obj.getIntContent(context))
    return ConcretePythonInterpreter.eval(emptyNamespace, expr.toString())
}