package org.usvm.interpreter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.language.types.PythonTypeSystem;
import org.usvm.machine.MockHeader;
import org.usvm.machine.utils.PyModelHolder;
import org.usvm.machine.PythonExecutionState;
import org.usvm.machine.UPythonContext;
import org.usvm.machine.interpreters.operations.tracing.PathDiversionException;
import org.usvm.machine.symbolicobjects.ConverterToPythonObject;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
    public Set<UninterpretedSymbolicPythonObject> delayedNonNullObjects = new HashSet<>();
    public PythonTypeSystem typeSystem;

    public ConcolicRunContext(
            @NotNull PythonExecutionState curState,
            UPythonContext ctx,
            PyModelHolder modelHolder,
            PythonTypeSystem typeSystem,
            boolean allowPathDiversion
    ) {
        this.curState = curState;
        this.ctx = ctx;
        this.modelHolder = modelHolder;
        this.allowPathDiversion = allowPathDiversion;
        this.typeSystem = typeSystem;
        if (curState.getMeta().getLastConverter() != null) {
            this.converter = curState.getMeta().getLastConverter();
        } else {
            this.converter = new ConverterToPythonObject(ctx, typeSystem, modelHolder);
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