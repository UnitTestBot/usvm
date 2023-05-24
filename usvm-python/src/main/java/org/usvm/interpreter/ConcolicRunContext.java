package org.usvm.interpreter;

import org.usvm.StepScope;
import org.usvm.language.PythonType;

public class ConcolicRunContext {
    public StepScope<PythonExecutionState, PythonType> stepScope;

    ConcolicRunContext(StepScope<PythonExecutionState, PythonType> stepScope) {
        this.stepScope = stepScope;
    }
}
