package org.usvm.machine.interpreters.operations.descriptors

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.MemberDescriptor
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

object ListAppendDescriptor: MemberDescriptor() {
    override fun getMember(owner: UninterpretedSymbolicPythonObject): SymbolForCPython {
        return ConcretePythonInterpreter.constructListAppendMethod(owner)
    }
}