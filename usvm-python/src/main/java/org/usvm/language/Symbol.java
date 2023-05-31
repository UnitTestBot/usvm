package org.usvm.language;

import io.ksmt.expr.KExpr;

@SuppressWarnings("rawtypes")
public class Symbol {
    public KExpr expr;
    public Symbol(KExpr expr) {
        this.expr = expr;
    }
}
