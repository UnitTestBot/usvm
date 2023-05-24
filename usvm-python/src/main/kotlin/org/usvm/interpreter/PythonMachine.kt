package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.language.Callable
import org.usvm.language.Program

class PythonMachine(
    val program: Program,
): UMachine<PythonExecutionState, Callable>() {
    override fun getInterpreter(target: Callable): USVMPythonInterpreter =
        USVMPythonInterpreter(ctx, globals, target)

    override fun getPathSelector(target: Callable): UPathSelector<PythonExecutionState> {
        TODO("Not yet implemented")
    }

    private val ctx = UContext(PythonComponents)
    private val globals = ConcretePythonInterpreter.getNewNamespace()
    init {
        ConcretePythonInterpreter.concreteRun(globals, program.asString)
    }
}