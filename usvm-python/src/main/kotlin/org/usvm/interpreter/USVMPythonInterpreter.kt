package org.usvm.interpreter

import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UContext
import org.usvm.UInterpreter
import org.usvm.language.Callable
import org.usvm.language.PythonType

typealias PythonStepScope = StepScope<PythonExecutionState, PythonType>

class USVMPythonInterpreter(
    private val ctx: UContext,
    private val namespace: PythonNamespace,
    private val callable: Callable
) : UInterpreter<PythonExecutionState>() {
    private val functionRef = callable.reference(namespace)
    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> {
        val scope = PythonStepScope(ctx, state)

        ConcretePythonInterpreter.concolicRun(namespace, functionRef, arrayOf(), scope)

        return scope.stepResult()
    }
}