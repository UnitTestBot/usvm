package org.usvm.language;

import org.jetbrains.annotations.Nullable;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

public class SymbolForCPython {
    @Nullable public UninterpretedSymbolicPythonObject obj;
    public long symbolicTpCall = 0;
    public SymbolForCPython(@Nullable UninterpretedSymbolicPythonObject obj, long symbolicTpCall) {
        this.obj = obj;
        this.symbolicTpCall = symbolicTpCall;
    }

    // TODO: consider descriptor?
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SymbolForCPython))
            return false;
        if (obj == null)
            return ((SymbolForCPython) other).obj == null && symbolicTpCall == ((SymbolForCPython) other).symbolicTpCall;
        return this.obj.equals(((SymbolForCPython) other).obj) && symbolicTpCall == ((SymbolForCPython) other).symbolicTpCall;
    }

    @Override
    public int hashCode() {
        if (obj == null)
            return 0;
        return obj.hashCode();
    }
}
