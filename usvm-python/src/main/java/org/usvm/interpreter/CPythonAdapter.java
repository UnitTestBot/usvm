package org.usvm.interpreter;

import org.usvm.language.Symbol;

public class CPythonAdapter {
    public native void run(String code, String functionName, Symbol[] args_symbolic);

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
