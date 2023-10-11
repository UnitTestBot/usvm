package org.usvm.machine.interpreters.operations.symbolicmethods

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.operations.basic.handlerLoadConstKt

fun generateNone(ctx: ConcolicRunContext): SymbolForCPython =
    SymbolForCPython(handlerLoadConstKt(ctx, PythonObject(ConcretePythonInterpreter.pyNoneRef)), 0)