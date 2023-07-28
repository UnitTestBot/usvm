package org.usvm.language;

import org.usvm.interpreter.symbolicobjects.InterpretedInputSymbolicPythonObject;

public class VirtualPythonObject {
    public InterpretedInputSymbolicPythonObject interpretedObj;
    public VirtualPythonObject(InterpretedInputSymbolicPythonObject interpretedObj) {
        this.interpretedObj = interpretedObj;
    }
}
