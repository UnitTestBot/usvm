package org.usvm.machine.interpreters.symbolic.operations.symbolicmethods

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.interpreters.symbolic.operations.basic.handlerLoadConstKt

fun generateNone(ctx: ConcolicRunContext): SymbolForCPython =
    SymbolForCPython(handlerLoadConstKt(ctx, PyObject(ConcretePythonInterpreter.pyNoneRef)), 0)
