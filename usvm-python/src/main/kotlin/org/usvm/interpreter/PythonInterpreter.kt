package org.usvm.interpreter

import org.usvm.StepResult
import org.usvm.UContext
import org.usvm.UInterpreter

class PythonInterpreter(
    private val ctx: UContext
) : UInterpreter<PythonExecutionState>() {
    override fun step(state: PythonExecutionState): StepResult<PythonExecutionState> {
        TODO("Not yet implemented")
    }
}