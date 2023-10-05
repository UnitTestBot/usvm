package org.usvm.language;

import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject;

public class VirtualPythonObject {
    public InterpretedInputSymbolicPythonObject interpretedObj;
    public VirtualPythonObject(InterpretedInputSymbolicPythonObject interpretedObj) {
        this.interpretedObj = interpretedObj;
    }
}
