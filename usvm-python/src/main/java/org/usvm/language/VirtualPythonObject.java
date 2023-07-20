package org.usvm.language;

import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject;

public class VirtualPythonObject {
    public InterpretedSymbolicPythonObject interpretedObj;
    public SymbolForCPython origin;
    public VirtualPythonObject(InterpretedSymbolicPythonObject interpretedObj, SymbolForCPython origin) {
        this.interpretedObj = interpretedObj;
        this.origin = origin;
    }
}
