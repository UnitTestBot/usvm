package org.usvm.interpreter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.machine.types.PythonTypeSystem;
import org.usvm.machine.MockHeader;
import org.usvm.machine.interpreters.symbolic.operations.tracing.PathDiversionException;
import org.usvm.machine.interpreters.symbolic.operations.tracing.SymbolicHandlerEvent;
import org.usvm.machine.PyState;
import org.usvm.machine.PyContext;
import org.usvm.machine.model.PyModelHolder;
import org.usvm.machine.symbolicobjects.rendering.PyObjectModelBuilder;
import org.usvm.machine.symbolicobjects.rendering.PyObjectRenderer;
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction;

import java.util.*;
import java.util.concurrent.Callable;


public class ConcolicRunContext {
    @Nullable
    public PyState curState;
    public PyContext ctx;
    public ArrayList<PyState> forkedStates = new ArrayList<>();
    public List<SymbolicHandlerEvent<Object>> pathPrefix;
    public MockHeader curOperation = null;
    public PyModelHolder modelHolder;
    public boolean allowPathDiversion;
    public PythonTypeSystem typeSystem;
    public PythonMachineStatisticsOnFunction statistics;
    public int maxInstructions;
    public int instructionCounter = 0;
    public boolean usesVirtualInputs = false;
    public Callable<Boolean> isCancelled;
    public PyObjectModelBuilder builder = null;
    public PyObjectRenderer renderer = null;

    public ConcolicRunContext(
            @NotNull PyState curState,
            PyContext ctx,
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
        this.maxInstructions = maxInstructions;
        this.isCancelled = isCancelled;
    }

    public void pathDiversion() throws PathDiversionException {
        if (curState != null)
            curState.getMeta().setModelDied(true);
        if (allowPathDiversion) {
            curState = null;
        } else {
            throw PathDiversionException.INSTANCE;
        }
    }
}