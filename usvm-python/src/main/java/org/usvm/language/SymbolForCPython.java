package org.usvm.language;

import org.jetbrains.annotations.Nullable;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

public class SymbolForCPython {
    @Nullable public UninterpretedSymbolicPythonObject obj;
    public SymbolForCPython(@Nullable UninterpretedSymbolicPythonObject obj) {
        this.obj = obj;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SymbolForCPython))
            return false;
        if (obj == null)
            return ((SymbolForCPython) other).obj == null;
        return this.obj.equals(((SymbolForCPython) other).obj);
    }

    @Override
    public int hashCode() {
        if (obj == null)
            return 0;
        return obj.hashCode();
    }
}
