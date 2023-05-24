package org.usvm.interpreter

import org.usvm.UInterpreter
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.language.Callable
import org.usvm.language.Program

class PythonMachine(
    val program: Program
): UMachine<PythonExecutionState, Callable>() {
    override fun getInterpreter(target: Callable): UInterpreter<PythonExecutionState> {
        TODO("Not yet implemented")
    }

    override fun getPathSelector(target: Callable): UPathSelector<PythonExecutionState> {
        TODO("Not yet implemented")
    }

}