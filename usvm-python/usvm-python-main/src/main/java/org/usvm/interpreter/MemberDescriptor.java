package org.usvm.interpreter;

import org.jetbrains.annotations.Nullable;
import org.usvm.language.SymbolForCPython;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

public abstract class MemberDescriptor {
    @Nullable
    public abstract SymbolForCPython getMember(ConcolicRunContext ctx, @Nullable UninterpretedSymbolicPythonObject owner);
}