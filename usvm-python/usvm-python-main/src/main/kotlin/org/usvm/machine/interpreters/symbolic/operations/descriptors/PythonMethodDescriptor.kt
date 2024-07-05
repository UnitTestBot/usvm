package org.usvm.machine.interpreters.symbolic.operations.descriptors

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

object PythonMethodDescriptor : MemberDescriptor {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython {
        requireNotNull(owner) { "Python method must always have an owner" }
        return ConcretePythonInterpreter.constructPartiallyAppliedPythonMethod(SymbolForCPython(owner, 0))
    }
}
