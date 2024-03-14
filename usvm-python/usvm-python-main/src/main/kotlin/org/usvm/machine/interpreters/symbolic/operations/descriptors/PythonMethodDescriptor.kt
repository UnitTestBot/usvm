package org.usvm.machine.interpreters.symbolic.operations.descriptors

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.MemberDescriptor
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

object PythonMethodDescriptor : MemberDescriptor() {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython {
        require(owner != null) { "Python method must always have an owner" }
        return ConcretePythonInterpreter.constructPartiallyAppliedPythonMethod(SymbolForCPython(owner, 0))
    }
}
