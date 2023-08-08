package org.usvm.interpreter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.machine.MockHeader;
import org.usvm.machine.PyModelHolder;
import org.usvm.machine.PythonExecutionState;
import org.usvm.machine.UPythonContext;
import org.usvm.machine.interpreters.operations.tracing.PathDiversionException;
import org.usvm.machine.symbolicobjects.ConverterToPythonObject;

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
    public ConverterToPythonObject converter;

    public ConcolicRunContext(
            @NotNull PythonExecutionState curState,
            UPythonContext ctx,
            PyModelHolder modelHolder,
            boolean allowPathDiversion
    ) {
        this.curState = curState;
        this.ctx = ctx;
        this.modelHolder = modelHolder;
        this.allowPathDiversion = allowPathDiversion;
        if (curState.getMeta().getLastConverter() != null) {
            this.converter = curState.getMeta().getLastConverter();
        } else {
            this.converter = new ConverterToPythonObject(ctx, modelHolder);
        }
    }

    public void pathDiversion() throws PathDiversionException {
        if (allowPathDiversion) {
            curState = null;
        } else {
            throw PathDiversionException.INSTANCE;
        }
    }
}