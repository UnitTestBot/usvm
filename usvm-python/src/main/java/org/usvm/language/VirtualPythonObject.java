package org.usvm.language;

import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject;

public class VirtualPythonObject {
    public InterpretedSymbolicPythonObject obj;
    public SymbolForCPython symbol;
    public VirtualPythonObject(InterpretedSymbolicPythonObject obj, SymbolForCPython symbol) {
        this.obj = obj;
        this.symbol = symbol;
    }
}
