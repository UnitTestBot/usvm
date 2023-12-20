package org.usvm.language;

import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject;

public class VirtualPythonObject {
    public int interpretedObjRef;
    public VirtualPythonObject(InterpretedInputSymbolicPythonObject interpretedObj) {
        this.interpretedObjRef = interpretedObj.getAddress().getAddress();
    }
    public VirtualPythonObject(int interpretedObjRef) {
        this.interpretedObjRef = interpretedObjRef;
    }
}
