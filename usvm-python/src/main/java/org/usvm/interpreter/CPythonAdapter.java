package org.usvm.interpreter;

import org.usvm.language.Symbol;

public class CPythonAdapter {
    public boolean isInitialized = false;
    public native void initializePython();
    public native void finalizePython();
    public native long getNewNamespace();  // returns reference to a new dict
    public native int concreteRun(long globals, String code);  // returns 0 on success
    public native long eval(long globals, String expr);  // returns PyObject *
    public native int concolicRun(long globals, long functionRef, Symbol[] symbolicArgs, ConcolicRunContext context);

    static {
        System.loadLibrary("cpythonadapter");
    }

    static Symbol handler(String cmd, Symbol[] args) {
        System.out.print("Hello from Java! Args:");
        for (Symbol arg : args)
            System.out.print(" " + arg.repr);
        System.out.println();
        System.out.flush();
        return new Symbol(cmd);
    }
}
