package org.usvm.interpreter;

import io.ksmt.expr.KExpr;
import org.usvm.language.PythonOPCODE;
import org.usvm.language.SymbolForCPython;

import java.util.function.Supplier;

import static org.usvm.interpreter.operations.ForkKt.handlerForkKt;
import static org.usvm.interpreter.operations.LongKt.*;

@SuppressWarnings("unused")
public class CPythonAdapter {
    public boolean isInitialized = false;
    public native void initializePython();
    public native void finalizePython();
    public native long getNewNamespace();  // returns reference to a new dict
    public native int concreteRun(long globals, String code);  // returns 0 on success
    public native long eval(long globals, String expr);  // returns PyObject *
    public native int concolicRun(long globals, long functionRef, long[] concreteArgs, SymbolForCPython[] symbolicArgs, ConcolicRunContext context);

    static {
        System.loadLibrary("cpythonadapter");
    }

    public static void handlerInstruction(ConcolicRunContext context, int instruction) {
        context.instructionCounter++;
        // TODO: check consistency
        if (context.instructionCounter > context.curState.getPath().size())
            context.curState.setPath(context.curState.getPath().add(new PythonOPCODE(instruction)));
    }

    @SuppressWarnings("rawtypes")
    private static <T extends KExpr> SymbolForCPython methodWrapper(ConcolicRunContext context, Supplier<T> valueSupplier) {
        T result = valueSupplier.get();
        if (result != null)
            return new SymbolForCPython(result);
        return null;
    }

    public static SymbolForCPython handlerLoadConstLong(ConcolicRunContext context, long value) {
        return methodWrapper(context, () -> context.ctx.mkIntNum(value));
    }

    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        handlerForkKt(context, cond.expr);
    }

    public static SymbolForCPython handlerGTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerGTLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerLTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerLTLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerEQLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerEQLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerNELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerNELongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerGELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerGELongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerLELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerLELongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerADDLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerADDLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerSUBLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerSUBLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerMULLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerMULLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerDIVLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerDIVLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerREMLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerREMLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerPOWLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, () -> handlerPOWLongKt(context.ctx, left.expr, right.expr));
    }
}
