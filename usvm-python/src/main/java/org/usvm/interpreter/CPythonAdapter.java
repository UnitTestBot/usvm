package org.usvm.interpreter;

import org.usvm.language.Symbol;

import static org.usvm.interpreter.CPythonAdapterHandlersKt.handlerForkResultKt;

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

    public static Symbol handlerLoadConstLong(ConcolicRunContext context, long value) {
        return new Symbol(context.ctx.mkIntNum(value));
    }

    @SuppressWarnings("unchecked")
    public static void handlerFork(ConcolicRunContext context, Symbol cond) {
        if (cond.expr.getSort() != context.ctx.getBoolSort()) {
            context.openedCondition = null;
        } else {
            context.openedCondition = cond.expr;
        }
    }

    public static void handlerForkResult(ConcolicRunContext context, boolean result) {
        handlerForkResultKt(context.ctx, context.openedCondition, context.stepScope, result);
    }
}
