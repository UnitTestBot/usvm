package org.usvm.interpreter;

import org.usvm.language.Symbol;

public class CPythonAdapter {
    public native long initializePython();  // returns pointer to __main__ module, might be null
    public native void finalizePython();
    public native int concreteRun(long main_module, String code);
    public native long eval(long main_module, String expr);  // returns PyObject *
    public native void concolicRun(long main_modu, String functionName, Symbol[] args_symbolic);

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
