package org.usvm.language;

import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject;

public class SymbolForCPython {
    public UninterpretedSymbolicPythonObject obj;
    public SymbolForCPython(UninterpretedSymbolicPythonObject obj) {
        this.obj = obj;
    }
}
