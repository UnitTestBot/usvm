package org.usvm.interpreter;

import org.usvm.UContext;

import java.util.ArrayList;

public class ConcolicRunContext {
    public PythonExecutionState curState;
    public UContext ctx;
    public ArrayList<PythonExecutionState> forkedStates = new ArrayList<>();
    public int instructionCounter = 0;

    ConcolicRunContext(PythonExecutionState curState, UContext ctx) {
        this.curState = curState;
        this.ctx = ctx;
        // forkedStates.add(curState);
    }
}
