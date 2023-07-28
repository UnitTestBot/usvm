package org.usvm.interpreter;

import org.jetbrains.annotations.NotNull;
import org.usvm.UContext;

import java.util.ArrayList;

public class ConcolicRunContext {
    public PythonExecutionState curState;
    public UPythonContext ctx;
    public ArrayList<PythonExecutionState> forkedStates = new ArrayList<>();
    public int instructionCounter = 0;
    public MockHeader curOperation = null;
    public PyModelHolder modelHolder;

    ConcolicRunContext(@NotNull PythonExecutionState curState, UPythonContext ctx, PyModelHolder modelHolder) {
        this.curState = curState;
        this.ctx = ctx;
        this.modelHolder = modelHolder;
    }
}