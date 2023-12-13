package org.usvm.machine.interpreters.symbolic.operations.symbolicmethods

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PythonObject
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerLoadConstKt

fun generateNone(ctx: ConcolicRunContext): SymbolForCPython =
    SymbolForCPython(handlerLoadConstKt(ctx, PythonObject(ConcretePythonInterpreter.pyNoneRef)), 0)