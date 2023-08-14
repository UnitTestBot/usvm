package org.usvm.language;

import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

public class SymbolForCPython {
    public UninterpretedSymbolicPythonObject obj;
    public SymbolForCPython(UninterpretedSymbolicPythonObject obj) {
        this.obj = obj;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SymbolForCPython))
            return false;
        return ((SymbolForCPython) other).obj.equals(this.obj);
    }

    @Override
    public int hashCode() {
        return obj.hashCode();
    }
}
