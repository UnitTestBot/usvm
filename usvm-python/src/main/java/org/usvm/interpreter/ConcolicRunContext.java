package org.usvm.interpreter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.interpreter.operations.tracing.PathDiversionException;

import java.util.ArrayList;

public class ConcolicRunContext {
    @Nullable
    public PythonExecutionState curState;
    public UPythonContext ctx;
    public ArrayList<PythonExecutionState> forkedStates = new ArrayList<>();
    public int instructionCounter = 0;
    public MockHeader curOperation = null;
    public PyModelHolder modelHolder;
    public boolean allowPathDiversion;

    ConcolicRunContext(
            @NotNull PythonExecutionState curState,
            UPythonContext ctx,
            PyModelHolder modelHolder,
            boolean allowPathDiversion
    ) {
        this.curState = curState;
        this.ctx = ctx;
        this.modelHolder = modelHolder;
        this.allowPathDiversion = allowPathDiversion;
    }

    public void pathDiversion() throws PathDiversionException {
        if (allowPathDiversion) {
            curState = null;
        } else {
            throw PathDiversionException.INSTANCE;
        }
    }
}