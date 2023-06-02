package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.language.Attribute
import org.usvm.language.Callable
import org.usvm.language.PythonProgram
import org.usvm.language.PythonType
import org.usvm.ps.DfsPathSelector

class PythonMachine(
    private val program: PythonProgram,
): UMachine<PythonExecutionState, Callable>() {
    private val ctx = UContext(PythonComponents)
    private val globals = ConcretePythonInterpreter.getNewNamespace()
    private val solver = ctx.solver<Attribute, PythonType, Callable>()
    init {
        ConcretePythonInterpreter.concreteRun(globals, program.asString)
    }
    override fun getInterpreter(target: Callable): USVMPythonInterpreter =
        USVMPythonInterpreter(ctx, globals, target)

    @Suppress("UNUSED_PARAMETER")
    private fun getInitialState(target: Callable): PythonExecutionState {
        return PythonExecutionState(
            ctx,
            target,
            models = listOf(solver.emptyModel())
        )
    }

    override fun getPathSelector(target: Callable): UPathSelector<PythonExecutionState> {
        val ps = DfsPathSelector<PythonExecutionState>()
        val initialState = getInitialState(target)
        ps.add(sequenceOf(initialState))
        return ps
    }

    fun analyze(callable: Callable) {
        var cnt = 0
        run(
            callable,
            onState = { cnt += 1 },
            continueAnalyzing = { !it.wasExecuted },
            shouldStop = { cnt >= 100 }
        )
    }

    override fun close() {
        solver.close()
        ctx.close()
    }
}