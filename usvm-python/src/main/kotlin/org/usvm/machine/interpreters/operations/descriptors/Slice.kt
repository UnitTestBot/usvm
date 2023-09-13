package org.usvm.machine.interpreters.operations.descriptors

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.MemberDescriptor
import org.usvm.language.SymbolForCPython
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

object SliceStartDescriptor: MemberDescriptor() {
    override fun getMember(ctx: ConcolicRunContext, owner: UninterpretedSymbolicPythonObject): SymbolForCPython? {
        TODO("Not yet implemented")
    }
}