package org.usvm.interpreter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.language.types.PythonType;
import org.usvm.language.types.PythonTypeSystem;
import org.usvm.machine.MockHeader;
import org.usvm.machine.interpreters.operations.tracing.SymbolicHandlerEvent;
import org.usvm.machine.utils.PyModelHolder;
import org.usvm.machine.PythonExecutionState;
import org.usvm.machine.UPythonContext;
import org.usvm.machine.interpreters.operations.tracing.PathDiversionException;
import org.usvm.machine.symbolicobjects.ConverterToPythonObject;
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction;
import org.usvm.types.UTypeStream;

import java.util.*;
import java.util.concurrent.Callable;


public class ConcolicRunContext {
    @Nullable
    public PythonExecutionState curState;
    public UPythonContext ctx;
    public ArrayList<PythonExecutionState> forkedStates = new ArrayList<>();
    public List<SymbolicHandlerEvent<Object>> pathPrefix;
    public MockHeader curOperation = null;
    public PyModelHolder modelHolder;
    public boolean allowPathDiversion;
    public ConverterToPythonObject converter;
    public PythonTypeSystem typeSystem;
    public PythonMachineStatisticsOnFunction statistics;
    public int maxInstructions;
    public int instructionCounter = 0;
    public boolean usesVirtualInputs = false;
    public Callable<Boolean> isCancelled;

    public ConcolicRunContext(
            @NotNull PythonExecutionState curState,
            UPythonContext ctx,
            PyModelHolder modelHolder,
            PythonTypeSystem typeSystem,
            boolean allowPathDiversion,
            PythonMachineStatisticsOnFunction statistics,
            int maxInstructions,
            Callable<Boolean> isCancelled
    ) {
        this.curState = curState;
        this.ctx = ctx;
        this.modelHolder = modelHolder;
        this.allowPathDiversion = allowPathDiversion;
        this.typeSystem = typeSystem;
        this.pathPrefix = curState.buildPathAsList();
        this.statistics = statistics;
        if (curState.getMeta().getLastConverter() != null) {
            this.converter = curState.getMeta().getLastConverter();
        } else {
            this.converter = new ConverterToPythonObject(ctx, typeSystem, modelHolder);
        }
        this.maxInstructions = maxInstructions;
        this.isCancelled = isCancelled;
    }

    public void pathDiversion() throws PathDiversionException {
        if (allowPathDiversion) {
            curState = null;
        } else {
            throw PathDiversionException.INSTANCE;
        }
    }
}