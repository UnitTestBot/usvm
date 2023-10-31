package org.usvm.machine.interpreters.operations.descriptors

import org.usvm.annotations.ids.SymbolicMethodId
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.MemberDescriptor
import org.usvm.language.SymbolForCPython
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

class MethodDescriptor(private val id: SymbolicMethodId): MemberDescriptor() {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython {
        return ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(owner?.let { SymbolForCPython(it, 0) }, id)
    }
}