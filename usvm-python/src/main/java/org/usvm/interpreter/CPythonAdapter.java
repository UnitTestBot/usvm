package org.usvm.interpreter;

import io.ksmt.expr.KExpr;
import kotlin.Unit;
import org.usvm.language.PythonInstruction;
import org.usvm.language.SymbolForCPython;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.usvm.interpreter.operations.ForkKt.handlerForkKt;
import static org.usvm.interpreter.operations.LongKt.*;
import static org.usvm.interpreter.operations.PathTracingKt.withTracing;

@SuppressWarnings("unused")
public class CPythonAdapter {
    public boolean isInitialized = false;
    public native void initializePython();
    public native void finalizePython();
    public native long getNewNamespace();  // returns reference to a new dict
    public native int concreteRun(long globals, String code);  // returns 0 on success
    public native long eval(long globals, String expr);  // returns PyObject *
    public native long concreteRunOnFunctionRef(long globals, long functionRef, long[] concreteArgs);
    public native long concolicRun(long globals, long functionRef, long[] concreteArgs, SymbolForCPython[] symbolicArgs, ConcolicRunContext context);
    public native void printPythonObject(long object);
    public native String getPythonObjectRepr(long object);
    public native String getPythonObjectTypeName(long object);

    static {
        System.loadLibrary("cpythonadapter");
    }

    public static void handlerInstruction(ConcolicRunContext context, int instruction) {
        withTracing(context, new NextInstruction(new PythonInstruction(instruction)), () -> Unit.INSTANCE);
    }

    @SuppressWarnings("rawtypes")
    private static SymbolForCPython wrap(KExpr expr) {
        if (expr == null)
            return null;
        return new SymbolForCPython(expr);
    }

    @SuppressWarnings("rawtypes")
    private static <T extends KExpr> SymbolForCPython methodWrapper(
            ConcolicRunContext context,
            int methodId,
            List<SymbolForCPython> operands,
            Supplier<T> valueSupplier
    ) {
        return withTracing(
                context,
                new MethodQueryParameters(methodId, operands),
                () -> {
                    T result = valueSupplier.get();
                    return wrap(result);
                }
        );
    }

    public static SymbolForCPython handlerLoadConstLong(ConcolicRunContext context, long value) {
        return withTracing(context, new LoadConstParameters(value), () -> wrap(context.ctx.mkIntNum(value)));
    }

    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        handlerForkKt(context, cond.expr);
    }

    public static SymbolForCPython handlerGTLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerGTLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerLTLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerLTLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerEQLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerEQLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerNELong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerNELongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerGELong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerGELongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerLELong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerLELongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerADDLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerADDLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerSUBLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerSUBLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerMULLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerMULLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerDIVLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerDIVLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerREMLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerREMLongKt(context.ctx, left.expr, right.expr));
    }

    public static SymbolForCPython handlerPOWLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerPOWLongKt(context.ctx, left.expr, right.expr));
    }
}
