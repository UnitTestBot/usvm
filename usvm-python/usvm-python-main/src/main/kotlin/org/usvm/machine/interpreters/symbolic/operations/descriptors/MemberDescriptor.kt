package org.usvm.machine.interpreters.symbolic.operations.descriptors

import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

interface MemberDescriptor {
    fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject?): SymbolForCPython?
}
