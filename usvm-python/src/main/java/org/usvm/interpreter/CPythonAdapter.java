package org.usvm.interpreter;

import io.ksmt.expr.KExpr;
import org.usvm.language.Instruction;
import org.usvm.language.Symbol;

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
    public native int concolicRun(long globals, long functionRef, long[] concreteArgs, Symbol[] symbolicArgs, ConcolicRunContext context);

    static {
        System.loadLibrary("cpythonadapter");
    }

    public static void handlerInstruction(ConcolicRunContext context, int instruction) {
        context.instructionCounter++;
        // TODO: check consistency
        if (context.instructionCounter > context.curState.getPath().size())
            context.curState.setPath(context.curState.getPath().add(new Instruction(instruction)));
    }

    @SuppressWarnings("rawtypes")
    private static <T extends KExpr> Symbol methodWrapper(ConcolicRunContext context, Supplier<T> valueSupplier) {
        T result = valueSupplier.get();
        if (result != null)
            return new Symbol(result);
        return null;
    }

    public static Symbol handlerLoadConstLong(ConcolicRunContext context, long value) {
        return new Symbol(context.ctx.mkIntNum(value));
    }

    public static void handlerFork(ConcolicRunContext context, Symbol cond) {
        handlerForkKt(context, cond.expr);
    }

    public static Symbol handlerGTLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerGTLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerLTLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerLTLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerEQLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerEQLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerNELong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerNELongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerGELong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerGELongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerLELong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerLELongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerADDLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerADDLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerSUBLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerSUBLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerMULLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerMULLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerDIVLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerDIVLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerREMLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerREMLongKt(context.ctx, left.expr, right.expr));
    }

    public static Symbol handlerPOWLong(ConcolicRunContext context, Symbol left, Symbol right) {
        return methodWrapper(context, () -> handlerPOWLongKt(context.ctx, left.expr, right.expr));
    }
}
