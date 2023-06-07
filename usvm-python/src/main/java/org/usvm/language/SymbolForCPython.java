package org.usvm.language;

import io.ksmt.expr.KExpr;

@SuppressWarnings("rawtypes")
public class SymbolForCPython {
    public KExpr expr;
    public SymbolForCPython(KExpr expr) {
        this.expr = expr;
    }
}
