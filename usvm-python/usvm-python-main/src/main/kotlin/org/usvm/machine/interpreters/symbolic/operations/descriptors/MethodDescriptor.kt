package org.usvm.machine.interpreters.symbolic.operations.descriptors

import org.usvm.annotations.ids.SymbolicMethodId
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

class MethodDescriptor(private val id: SymbolicMethodId) : MemberDescriptor {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython {
        return ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(
            owner?.let { SymbolForCPython(it, 0) },
            id
        )
    }
}
