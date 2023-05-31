package org.usvm.interpreter;

import io.ksmt.expr.KExpr;
import io.ksmt.sort.KBoolSort;
import org.usvm.StepScope;
import org.usvm.UContext;
import org.usvm.language.PythonType;
import org.usvm.language.Symbol;

public class ConcolicRunContext {
    public StepScope<PythonExecutionState, PythonType> stepScope;
    public UContext ctx;
    KExpr<KBoolSort> openedCondition = null;

    ConcolicRunContext(StepScope<PythonExecutionState, PythonType> stepScope, UContext ctx) {
        this.stepScope = stepScope;
        this.ctx = ctx;
    }
}
