package org.usvm.language;

import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

public class SymbolForCPython {
    public UninterpretedSymbolicPythonObject obj;
    public SymbolForCPython(UninterpretedSymbolicPythonObject obj) {
        this.obj = obj;
    }
}
