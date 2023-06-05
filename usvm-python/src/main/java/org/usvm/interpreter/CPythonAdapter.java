package org.usvm.interpreter;

import io.ksmt.expr.KExpr;
import io.ksmt.sort.KBoolSort;
import io.ksmt.sort.KIntSort;
import org.usvm.language.Instruction;
import org.usvm.language.Symbol;

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

    public static Symbol handlerLoadConstLong(ConcolicRunContext context, long value) {
        return new Symbol(context.ctx.mkIntNum(value));
    }

    public static void handlerFork(ConcolicRunContext context, Symbol cond) {
        handlerForkKt(context, cond.expr);
    }

    public static Symbol handlerGTLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KBoolSort> res = handlerGTLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerLTLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KBoolSort> res = handlerLTLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerEQLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KBoolSort> res = handlerEQLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerNELong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KBoolSort> res = handlerNELongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerGELong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KBoolSort> res = handlerGELongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerLELong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KBoolSort> res = handlerLELongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerADDLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KIntSort> res = handlerADDLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerSUBLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KIntSort> res = handlerSUBLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerMULLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KIntSort> res = handlerMULLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerDIVLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KIntSort> res = handlerDIVLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }

    public static Symbol handlerREMLong(ConcolicRunContext context, Symbol left, Symbol right) {
        KExpr<KIntSort> res = handlerREMLongKt(context.ctx, left.expr, right.expr);
        if (res == null)
            return null;
        return new Symbol(res);
    }
}
